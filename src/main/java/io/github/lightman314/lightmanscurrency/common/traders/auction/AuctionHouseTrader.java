package io.github.lightman314.lightmanscurrency.common.traders.auction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.google.gson.JsonObject;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.client.gui.settings.SettingsTab;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.icon.IconData;
import io.github.lightman314.lightmanscurrency.common.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.common.traders.InteractionSlotData;
import io.github.lightman314.lightmanscurrency.common.traders.TradeContext;
import io.github.lightman314.lightmanscurrency.common.traders.TraderData;
import io.github.lightman314.lightmanscurrency.common.traders.TradeContext.TradeResult;
import io.github.lightman314.lightmanscurrency.common.traders.permissions.Permissions;
import io.github.lightman314.lightmanscurrency.common.traders.permissions.options.PermissionOption;
import io.github.lightman314.lightmanscurrency.common.traders.tradedata.TradeData;
import io.github.lightman314.lightmanscurrency.common.traders.tradedata.auction.AuctionTradeData;
import io.github.lightman314.lightmanscurrency.core.ModItems;
import io.github.lightman314.lightmanscurrency.events.AuctionHouseEvent.AuctionEvent.AuctionBidEvent;
import io.github.lightman314.lightmanscurrency.events.AuctionHouseEvent.AuctionEvent.CreateAuctionEvent;
import io.github.lightman314.lightmanscurrency.items.WalletItem;
import io.github.lightman314.lightmanscurrency.menus.TraderMenu;
import io.github.lightman314.lightmanscurrency.menus.TraderStorageMenu;
import io.github.lightman314.lightmanscurrency.menus.traderstorage.TraderStorageTab;
import io.github.lightman314.lightmanscurrency.menus.traderstorage.auction.*;
import io.github.lightman314.lightmanscurrency.money.CoinValue;
import io.github.lightman314.lightmanscurrency.money.MoneyUtil;
import io.github.lightman314.lightmanscurrency.network.LightmansCurrencyPacketHandler;
import io.github.lightman314.lightmanscurrency.network.message.auction.MessageStartBid;
import io.github.lightman314.lightmanscurrency.upgrades.UpgradeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class AuctionHouseTrader extends TraderData {

	public static final ResourceLocation TYPE = new ResourceLocation(LightmansCurrency.MODID, "auction_house");
	
	public static final IconData ICON = IconData.of(new ResourceLocation(LightmansCurrency.MODID, "textures/gui/icons.png"), 96, 16);
	
	List<AuctionTradeData> trades = new ArrayList<>();
	
	Map<UUID,AuctionPlayerStorage> storage = new HashMap<>();
	
	@Override
	public boolean showOnTerminal() { return true; }
	@Override
	public boolean isCreative() { return true; }
	
	public AuctionHouseTrader() {
		super(TYPE);
		this.getOwner().SetCustomOwner(Component.translatable("gui.lightmanscurrency.universaltrader.auction.owner"));
		MinecraftForge.EVENT_BUS.register(this);
	}
	
	@Override
	public void onRemoved() {
		MinecraftForge.EVENT_BUS.unregister(this);
	}
	
	@Override
	public MutableComponent getName() { return Component.translatable("gui.lightmanscurrency.universaltrader.auction"); }
	
	@Override
	public int getTradeCount() { return this.trades.size(); }
	
	public AuctionTradeData getTrade(int tradeIndex) {
		try {
			return this.trades.get(tradeIndex);
		} catch(Exception e) { return null; }
	}
	
	public boolean hasPersistentAuction(String id) {
		for(AuctionTradeData trade : this.trades)
		{
			if(trade.isPersistentID(id) && trade.isValid())
				return true;
		}
		return false;
	}
	
	public int getTradeIndex(AuctionTradeData trade) {
		return this.trades.indexOf(trade);
	}
	
	//@Override
	public void markTradesDirty() {
		this.markDirty(this::saveTrades);
	}
	
	public AuctionPlayerStorage getStorage(Player player) { return getStorage(PlayerReference.of(player)); }
	
	public AuctionPlayerStorage getStorage(PlayerReference player) {
		if(player == null)
			return null;
		if(!this.storage.containsKey(player.id))
		{
			//Create new storage entry for the player
			this.storage.put(player.id, new AuctionPlayerStorage(player));
			this.markStorageDirty();
		}
		return this.storage.get(player.id);
	}
	
	public void markStorageDirty() {
		this.markDirty(this::saveStorage);
	}
	
	@SubscribeEvent
	public void onWorldTick(ServerTickEvent event) {
		//Only run on server, never run on client, as the client doesn't know who has the trader open.
		if(event.phase == Phase.END && event.side.isServer())
		{
			//Check if any trades have expired
			long currentTime = System.currentTimeMillis();
			boolean changed = false;
			//Can only delete trades if no player is currently using the trader, as we don't want to delete trades and mess up a trade index.
			boolean canDelete = this.getUserCount() <= 0;
			for(int i = 0; i < this.trades.size(); ++i)
			{
				AuctionTradeData trade = this.trades.get(i);
				//Check if the auction has timed out and should be executed
				if(trade.hasExpired(currentTime))
				{
					//Execute the trade if the time has run out
					//Includes sending notifications and payment to the relevant players storage
					trade.ExecuteTrade(this);
					changed = true;
				}
				//Check if the trade should be deleted
				if(canDelete && !trade.isValid())
				{
					//Delete the trade if it's no longer valid
					this.trades.remove(i);
					i--;
				}
			}
			if(changed) //Mark both trades and storage dirty
			{
				this.markDirty(this::saveTrades);
				this.markDirty(this::saveStorage);
			}
		}
	}
	
	@Override
	public int getPermissionLevel(PlayerReference player, String permission) {
		if(permission == Permissions.OPEN_STORAGE)
			return 1;
		return 0;
	}
	
	@Override
	public int getPermissionLevel(Player player, String permission) {
		if(permission == Permissions.OPEN_STORAGE)
			return 1;
		return 0;
	}
	
	@Override
	public void saveAdditional(CompoundTag compound) {
		
		this.saveTrades(compound);
		this.saveStorage(compound);
		
	}
	
	protected final void saveTrades(CompoundTag compound) {
		ListTag list = new ListTag();
		for(int i = 0; i < this.trades.size(); ++i)
		{
			list.add(this.trades.get(i).getAsNBT());
		}
		compound.put("Trades", list);
	}
	
	protected final CompoundTag saveStorage(CompoundTag compound) {
		ListTag list = new ListTag();
		this.storage.forEach((player,storage) -> list.add(storage.save(new CompoundTag())));
		compound.put("StorageData", list);
		return compound;
	}
	
	@Override
	public void loadAdditional(CompoundTag compound) {
		
		//Load trades
		if(compound.contains("Trades"))
		{
			this.trades.clear();
			ListTag tradeList = compound.getList("Trades", Tag.TAG_COMPOUND);
			for(int i = 0; i < tradeList.size(); ++i)
				this.trades.add(new AuctionTradeData(tradeList.getCompound(i)));
		}
		
		//Load storage
		if(compound.contains("StorageData"))
		{
			this.storage.clear();
			ListTag storageList = compound.getList("StorageData", Tag.TAG_COMPOUND);
			for(int i = 0; i < storageList.size(); ++i)
			{
				AuctionPlayerStorage storageEntry = new AuctionPlayerStorage(storageList.getCompound(i));
				if(storageEntry.getOwner() != null)
					this.storage.put(storageEntry.getOwner().id, storageEntry);
			}
		}
		
		if(!this.getOwner().hasOwner())
			this.getOwner().SetCustomOwner(Component.translatable("gui.lightmanscurrency.universaltrader.auction.owner"));
		
	}

	@Override
	public void addTrade(Player requestor) { }

	@Override
	public void removeTrade(Player requestor) {}
	
	public void addTrade(AuctionTradeData trade, boolean persistent) {
		
		CreateAuctionEvent.Pre e1 = new CreateAuctionEvent.Pre(this, trade, persistent);
		if(MinecraftForge.EVENT_BUS.post(e1))
			return;
		trade = e1.getAuction();
		
		trade.startTimer();
		if(trade.isValid())
		{
			this.trades.add(trade);
			this.markTradesDirty();
			
			CreateAuctionEvent.Post e2 = new CreateAuctionEvent.Post(this, trade, persistent);
			MinecraftForge.EVENT_BUS.post(e2);
		}
		else
			LightmansCurrency.LogError("Auction Trade is not fully valid. Unable to add it to the list.");
	}

	@Override
	public TradeResult ExecuteTrade(TradeContext context, int tradeIndex) {
		//Interaction should simply open the bid menu, so...
		if(!context.hasPlayer())
			return TradeResult.FAIL_NOT_SUPPORTED;
		else
		{
			//Open bid menu for the given trade index
			LightmansCurrencyPacketHandler.instance.send(LightmansCurrencyPacketHandler.getTarget(context.getPlayer()), new MessageStartBid(this.getID(), tradeIndex));
			return TradeResult.SUCCESS;
		}
	}
	
	public boolean makeBid(Player player, TraderMenu menu, int tradeIndex, CoinValue bidAmount) {
		
		AuctionTradeData trade = this.getTrade(tradeIndex);
		if(trade == null)
			return false;
		if(trade.hasExpired(System.currentTimeMillis()))
			return false;
		
		AuctionBidEvent.Pre e1 = new AuctionBidEvent.Pre(this, trade, player, bidAmount);
		if(MinecraftForge.EVENT_BUS.post(e1))
			return false;
		
		bidAmount = e1.getBidAmount();
		
		ItemStack wallet = LightmansCurrency.getWalletStack(player);
		long inventoryValue = MoneyUtil.getValue(menu.getCoinInventory());
		if(!wallet.isEmpty())
			inventoryValue += MoneyUtil.getValue(WalletItem.getWalletInventory(wallet));
    	if(inventoryValue < bidAmount.getRawValue())
    		return false;
		if(trade.tryMakeBid(this, player, bidAmount))
		{
			//Take money from the coin slots & players wallet second
			MoneyUtil.ProcessPayment(menu.getCoinInventory(), player, bidAmount);
			//Mark storage & trades dirty
			this.markDirty(this::saveTrades);
			this.markDirty(this::saveStorage);
			
			AuctionBidEvent.Post e2 = new AuctionBidEvent.Post(this, trade, player, bidAmount);
			MinecraftForge.EVENT_BUS.post(e2);
			return true;
		}
		return false;
		
	}

	@Override
	public List<? extends TradeData> getTradeData() { return this.trades; }

	@Override
	public ItemLike getCategoryItem() { return ModItems.TRADING_CORE.get(); }

	@Override
	public IconData getIcon() { return ICON; }

	@Override
	public boolean canMakePersistent() { return false; }
	
	@Override
	public void saveAdditionalPersistentData(CompoundTag compound) { }

	@Override
	public void loadAdditionalPersistentData(CompoundTag data) { }

	@Override
	public Function<TradeData,Boolean> getStorageDisplayFilter(TraderStorageMenu menu) {
		return trade -> {
			if(trade instanceof AuctionTradeData)
			{
				AuctionTradeData at = (AuctionTradeData)trade;
				//Only display if the trade owner is owned by the player.
				return at.isOwner(menu.player) && at.isValid();
			}
			return false;
		};
	}
	
	@Override
	public void initStorageTabs(TraderStorageMenu menu) {
		//Storage Tab
		menu.setTab(TraderStorageTab.TAB_TRADE_STORAGE, new AuctionStorageTab(menu));
		//Cancel Trade tab
		menu.setTab(TraderStorageTab.TAB_TRADE_ADVANCED, new AuctionTradeCancelTab(menu));
		//Create Trade tab
		menu.setTab(10, new AuctionCreateTab(menu));
	}
	
	@Override
	public boolean shouldRemove(MinecraftServer server) { return false; }

	@Override
	public void getAdditionalContents(List<ItemStack> contents) { }

	@Override
	protected MutableComponent getDefaultName() { return this.getName(); }

	@Override
	public boolean hasValidTrade() { return true; }

	@Override
	protected void saveAdditionalToJson(JsonObject json) { }

	@Override
	protected void loadAdditionalFromJson(JsonObject json) {}

	@Override
	public void addInteractionSlots(List<InteractionSlotData> interactionSlots) { }

	@Override
	protected boolean allowAdditionalUpgradeType(UpgradeType type) { return false; }

	@Override
	public int getTradeStock(int tradeIndex) { return 0; }

	@Override
	protected void addSettingsTabs(List<SettingsTab> tabs) { }

	@Override
	protected void addPermissionOptions(List<PermissionOption> options) { }
	
	@Override
	protected void modifyDefaultAllyPermissions(Map<String,Integer> defaultValues) { defaultValues.clear(); }
	
	@Override @Deprecated //Just load normally, as the Auction House data didn't change much.
	protected void loadExtraOldUniversalTraderData(CompoundTag compound) { this.loadAdditional(compound); }
	
	@Override @Deprecated
	protected void loadExtraOldBlockEntityData(CompoundTag compound) { }
	
}
