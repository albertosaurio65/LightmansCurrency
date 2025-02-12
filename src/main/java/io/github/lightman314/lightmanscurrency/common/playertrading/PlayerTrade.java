package io.github.lightman314.lightmanscurrency.common.playertrading;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.menus.PlayerTradeMenu;
import io.github.lightman314.lightmanscurrency.money.CoinValue;
import io.github.lightman314.lightmanscurrency.money.MoneyUtil;
import io.github.lightman314.lightmanscurrency.network.LightmansCurrencyPacketHandler;
import io.github.lightman314.lightmanscurrency.network.message.playertrading.SMessageUpdatePlayerTrade;
import io.github.lightman314.lightmanscurrency.util.InventoryUtil;
import io.github.lightman314.lightmanscurrency.util.TimeUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerTrade implements IPlayerTrade, MenuProvider {


    private boolean stillPending = true;
    public final long creationTime;
    public final int tradeID;
    private boolean completed = false;
    public boolean isCompleted() { return this.completed; }

    @Override
    public boolean isHost(@Nonnull Player player) { return player.getUUID() == this.hostPlayerID; }
    public boolean isGuest(@Nonnull Player player) { return player.getUUID() == this.guestPlayerID; }
    private final UUID hostPlayerID;
    @Override
    public Component getHostName() {
        ServerPlayer hostPlayer = this.getPlayer(this.hostPlayerID);
        return hostPlayer == null ? Component.literal("NULL") : hostPlayer.getName();
    }
    private final UUID guestPlayerID;
    @Override
    public Component getGuestName() {
        ServerPlayer guestPlayer = this.getPlayer(this.guestPlayerID);
        return guestPlayer == null ? Component.literal("NULL") : guestPlayer.getName();
    }

    private boolean playerMissing(@Nonnull UUID playerID) { return getPlayer(playerID) == null; }

    private ServerPlayer getPlayer(UUID playerID) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if(server != null)
            return server.getPlayerList().getPlayer(playerID);
        return null;
    }

    private final CoinValue hostMoney = new CoinValue();
    @Override
    public CoinValue getHostMoney() { return this.hostMoney.copy(); }
    private final CoinValue guestMoney = new CoinValue();
    @Override
    public CoinValue getGuestMoney() { return this.guestMoney.copy(); }

    private final SimpleContainer hostItems = new SimpleContainer(IPlayerTrade.ITEM_COUNT);
    @Override
    public Container getHostItems() { return this.hostItems; }
    private final SimpleContainer guestItems = new SimpleContainer(IPlayerTrade.ITEM_COUNT);
    @Override
    public Container getGuestItems() { return this.guestItems; }

    private int hostState = 0;
    @Override
    public int getHostState() { return this.hostState; }
    private int guestState = 0;
    @Override
    public int getGuestState() { return this.guestState; }

    public PlayerTrade(ServerPlayer host, ServerPlayer guest, int tradeID) {
        this.hostPlayerID = host.getUUID();
        this.guestPlayerID = guest.getUUID();
        this.tradeID = tradeID;
        this.creationTime = TimeUtil.getCurrentTime();
        this.hostItems.addListener(this::onContainerChange);
        this.guestItems.addListener(this::onContainerChange);
    }

    private void onContainerChange(Container container) {
        if(this.hostItems == container)
            this.onTradeEdit(true);
        if(this.guestItems == container)
            this.onTradeEdit(false);
        this.markDirty();
    }

    private void onTradeEdit(boolean wasHost)
    {
        if(wasHost)
        {
            this.guestState = 0;
            this.hostState = Math.min(this.hostState, 1);
        }
        else
        {
            this.hostState = 0;
            this.guestState = Math.min(this.guestState, 1);
        }
    }

    private boolean playerAbandonedTrade(boolean host) {
        ServerPlayer player = this.getPlayer(host ? this.hostPlayerID : this.guestPlayerID);
        if(player == null)
        {
            LightmansCurrency.LogWarning("The " + (host ? "host" : "guest") + " is no longer online. Flagging trade as abandoned.");
            return true;
        }
        if(!this.isInMenu(player))
        {
            LightmansCurrency.LogWarning("The " + (host ? "host" : "guest") + " is no longer in the trade menu. Flagging trade as abandoned.");
            return true;
        }
        return false;
    }

    private boolean isInMenu(Player player) {
        if(player != null && player.containerMenu instanceof PlayerTradeMenu menu)
            return menu.tradeID == tradeID;
        return false;
    }

    private void takeMenuAction(Consumer<PlayerTradeMenu> action) {
        this.ifInMenu(this.hostPlayerID, action);
        this.ifInMenu(this.guestPlayerID, action);
    }

    private void ifInMenu(UUID playerID, Consumer<PlayerTradeMenu> action) {
        Player player = this.getPlayer(playerID);
        if(player != null)
            this.ifInMenu(player, action);
    }

    private void ifInMenu(Player player, Consumer<PlayerTradeMenu> action) {
        if(player.containerMenu instanceof PlayerTradeMenu menu) {
            if(menu.tradeID == this.tradeID)
                action.accept(menu);
        }
    }

    public final void tryCloseMenu(UUID playerID) {
        ServerPlayer player = this.getPlayer(playerID);
        if(player != null)
            this.tryCloseMenu(player);
    }

    public final void tryCloseMenu(Player player) { if(this.isInMenu(player)) player.closeContainer(); }

    public final boolean requestAccepted(ServerPlayer player) {
        if(this.stillPending && this.isGuest(player))
        {
            this.stillPending = false;
            ServerPlayer host = this.getPlayer(this.hostPlayerID);
            ServerPlayer guest = this.getPlayer(this.guestPlayerID);
            if(host == null || guest == null)
            {
                LightmansCurrency.LogWarning("Trade Request accepted, but either the Host or Guest is no longer online.");
                return false;
            }
            //Open the Player Trading menu for both involved parties
            NetworkHooks.openScreen(host, this, this::writeAdditionalMenuData);
            NetworkHooks.openScreen(guest, this, this::writeAdditionalMenuData);
            LightmansCurrency.LogInfo("Trade Request accepted, and Player Trading menu should be open for both players.");
            return true;
        }
        if(!this.stillPending)
            LightmansCurrency.LogWarning("Trade Request accepted, but the trade is not in a pending state!");
        if(!this.isGuest(player))
            LightmansCurrency.LogWarning("Trade Request accepted by the wrong player.");
        return false;
    }

    public boolean shouldCancel() {
        if(this.completed)
            return true;
        if(this.stillPending)
        {
            if(this.playerMissing(this.hostPlayerID))
            {
                LightmansCurrency.LogInfo("Cancelling pending trade as the host is missing.");
                return true;
            }
            if(this.playerMissing(this.guestPlayerID))
            {
                LightmansCurrency.LogInfo("Cancelling pending trade as the guest is missing.");
                return true;
            }
            if(!TimeUtil.compareTime(IPlayerTrade.PENDING_DURATION, this.creationTime))
            {
                LightmansCurrency.LogInfo("Cancelling pending trade as the trade has expired.");
                return true;
            }
            return false;
        }
        else
            return this.playerAbandonedTrade(true) || this.playerAbandonedTrade(false);
    }

    public void onCancel() {
        if(this.stillPending || this.completed)
            return;
        //Close menu for both the host and the guest
        this.tryCloseMenu(this.hostPlayerID);
        this.tryCloseMenu(this.guestPlayerID);
    }

    private void tryExecuteTrade() {
        if(this.hostState < 2 || this.guestState < 2 || this.stillPending)
            return;

        ServerPlayer host = this.getPlayer(this.hostPlayerID);
        ServerPlayer guest = this.getPlayer(this.guestPlayerID);
        if(host == null || guest == null)
            return;

        //Confirm that payment can be taken successfully
        if(MoneyUtil.ProcessPayment(null, host, this.hostMoney.copy()))
        {
            if(MoneyUtil.ProcessPayment(null, guest, this.guestMoney.copy()))
            {
                //Flag trade as completed
                completed = true;

                //Give money/items to host
                MoneyUtil.ProcessChange(null, host, this.guestMoney.copy());
                for(int i = 0; i < this.guestItems.getContainerSize(); ++i)
                {
                    ItemStack stack = this.guestItems.getItem(i);
                    if(!stack.isEmpty())
                        ItemHandlerHelper.giveItemToPlayer(host, stack);
                }

                //Give money/items to guest
                MoneyUtil.ProcessChange(null, guest, this.hostMoney.copy());
                for(int i = 0; i < this.hostItems.getContainerSize(); ++i)
                {
                    ItemStack stack = this.hostItems.getItem(i);
                    if(!stack.isEmpty())
                        ItemHandlerHelper.giveItemToPlayer(guest, stack);
                }

                this.tryCloseMenu(host);
                this.tryCloseMenu(guest);

            }
            else //Refund host money if guest doesn't have enough money
                MoneyUtil.ProcessChange(null, host, this.hostMoney.copy());
        }

    }

    private ClientPlayerTrade getData() { return new ClientPlayerTrade(this.hostPlayerID, this.getHostName(), this.getGuestName(), this.hostMoney.copy(), this.guestMoney.copy(), InventoryUtil.copy(this.hostItems), InventoryUtil.copy(this.guestItems), this.hostState, this.guestState); }

    public void handleInteraction(Player player, CompoundTag message) {
        if(!this.isHost(player) && !this.isGuest(player))
            return;
        if(this.isHost(player))
        {
            if(message.contains("TogglePropose"))
            {
                if(this.hostState > 0)
                {
                    this.hostState = 0;
                    this.guestState = Math.min(this.guestState, 1);
                }
                else
                    this.hostState = 1;
            }
            else if(message.contains("ToggleActive"))
            {
                if(this.hostState == 2)
                    this.hostState = 1;
                else if(this.hostState == 1 && this.guestState > 0)
                {
                    this.hostState = 2;
                    this.tryExecuteTrade();
                }
            }
            else if(message.contains("ChangeMoney"))
            {
                this.hostMoney.load(message,"ChangeMoney");
                this.onTradeEdit(true);
            }
        }
        else if(this.isGuest(player))
        {
            if(message.contains("TogglePropose"))
            {
                if(this.guestState > 0)
                {
                    this.guestState = 0;
                    this.hostState = Math.min(this.hostState, 1);
                }
                else
                    this.guestState = 1;
            }
            else if(message.contains("ToggleActive"))
            {
                if(this.guestState == 2)
                    this.guestState = 1;
                else if(this.guestState == 1 && this.hostState > 0)
                {
                    this.guestState = 2;
                    this.tryExecuteTrade();
                }
            }
            else if(message.contains("ChangeMoney"))
            {
                this.guestMoney.load(message,"ChangeMoney");
                this.onTradeEdit(false);
            }
        }
        else
            return;
        this.markDirty();
    }

    public final void markDirty() {
        ClientPlayerTrade data = this.getData();
        final ServerPlayer hostPlayer = this.getPlayer(this.hostPlayerID);
        final ServerPlayer guestPlayer = this.getPlayer(this.guestPlayerID);
        if(hostPlayer != null)
            LightmansCurrencyPacketHandler.instance.send(PacketDistributor.PLAYER.with(() -> hostPlayer), new SMessageUpdatePlayerTrade(data));
        if(guestPlayer != null)
            LightmansCurrencyPacketHandler.instance.send(PacketDistributor.PLAYER.with(() -> guestPlayer), new SMessageUpdatePlayerTrade(data));

        this.takeMenuAction(PlayerTradeMenu::onTradeChange);
    }


    //Menu Handling/Opening
    @Override
    public @NotNull Component getDisplayName() { return Component.empty(); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowID, @NotNull Inventory inventory, @NotNull Player player) { return new PlayerTradeMenu(windowID, inventory, this.tradeID, this); }

    private void writeAdditionalMenuData(FriendlyByteBuf buffer) {
        buffer.writeInt(this.tradeID);
        this.getData().encode(buffer);
    }

}
