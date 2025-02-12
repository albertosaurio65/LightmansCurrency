package io.github.lightman314.lightmanscurrency.menus.traderstorage.item;

import java.util.function.Function;

import io.github.lightman314.lightmanscurrency.client.gui.screen.inventory.TraderStorageScreen;
import io.github.lightman314.lightmanscurrency.client.gui.screen.inventory.traderstorage.item.ItemTradeEditClientTab;
import io.github.lightman314.lightmanscurrency.common.traders.item.ItemTraderData;
import io.github.lightman314.lightmanscurrency.common.traders.permissions.Permissions;
import io.github.lightman314.lightmanscurrency.common.traders.tradedata.item.ItemTradeData;
import io.github.lightman314.lightmanscurrency.common.traders.tradedata.item.ItemTradeData.ItemTradeType;
import io.github.lightman314.lightmanscurrency.menus.TraderStorageMenu;
import io.github.lightman314.lightmanscurrency.menus.traderstorage.TraderStorageClientTab;
import io.github.lightman314.lightmanscurrency.menus.traderstorage.TraderStorageTab;
import io.github.lightman314.lightmanscurrency.money.CoinValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ItemTradeEditTab extends TraderStorageTab{

	public ItemTradeEditTab(TraderStorageMenu menu) { super(menu); }

	@Override
	@OnlyIn(Dist.CLIENT)
	public TraderStorageClientTab<?> createClientTab(TraderStorageScreen screen) { return new ItemTradeEditClientTab(screen, this); }

	@Override
	public boolean canOpen(Player player) { return this.menu.getTrader().hasPermission(player, Permissions.EDIT_TRADES); }
	
	private int tradeIndex = -1;
	public int getTradeIndex() { return this.tradeIndex; }
	public ItemTradeData getTrade() { 
		if(this.menu.getTrader() instanceof ItemTraderData)
		{
			ItemTraderData trader = (ItemTraderData)this.menu.getTrader();
			if(this.tradeIndex >= trader.getTradeCount() || this.tradeIndex < 0)
			{
				this.menu.changeTab(TraderStorageTab.TAB_TRADE_BASIC);
				this.menu.sendMessage(this.menu.createTabChangeMessage(TraderStorageTab.TAB_TRADE_BASIC, null));
				return null;
			}
			return ((ItemTraderData)this.menu.getTrader()).getTrade(this.tradeIndex);
		}
		return null;
	}
	
	@Override
	public void onTabOpen() { }

	@Override
	public void onTabClose() { }

	@Override
	public void addStorageMenuSlots(Function<Slot, Slot> addSlot) { }
	
	public void setTradeIndex(int tradeIndex) { this.tradeIndex = tradeIndex; }
	
	public void setType(ItemTradeData.ItemTradeType type) {
		ItemTradeData trade = this.getTrade();
		if(trade != null)
		{
			trade.setTradeType(type);
			this.menu.getTrader().markTradesDirty();
			if(this.menu.isClient())
			{
				CompoundTag message = new CompoundTag();
				message.putInt("NewType", type.index);
				this.menu.sendMessage(message);
			}
		}
	}
	
	public void setCustomName(int selectedSlot, String customName) {
		ItemTradeData trade = this.getTrade();
		if(trade != null)
		{
			trade.setCustomName(selectedSlot, customName);
			this.menu.getTrader().markTradesDirty();
			if(this.menu.isClient())
			{
				CompoundTag message = new CompoundTag();
				message.putInt("Slot", selectedSlot);
				message.putString("CustomName", customName);
				this.menu.sendMessage(message);
			}
		}
	}
	
	public void setPrice(CoinValue price) {
		ItemTradeData trade = this.getTrade();
		if(trade != null)
		{
			trade.setCost(price);
			this.menu.getTrader().markTradesDirty();
			if(this.menu.isClient())
			{
				CompoundTag message = new CompoundTag();
				price.save(message, "NewPrice");
				this.menu.sendMessage(message);
			}
		}
	}
	
	public void setSelectedItem(int selectedSlot, ItemStack stack) {
		ItemTradeData trade = this.getTrade();
		if(trade != null)
		{
			trade.setItem(stack, selectedSlot);
			this.menu.getTrader().markTradesDirty();
			if(this.menu.isClient())
			{
				CompoundTag message = new CompoundTag();
				message.putInt("Slot", selectedSlot);
				message.put("NewItem", stack.save(new CompoundTag()));
				this.menu.sendMessage(message);
			}
		}	
	}
	
	public void defaultInteraction(int slotIndex, ItemStack heldStack, int mouseButton) {
		ItemTradeData trade = this.getTrade();
		if(trade != null)
		{
			trade.onSlotInteraction(this, slotIndex, heldStack, mouseButton);
			if(this.menu.isClient())
			{
				CompoundTag message = new CompoundTag();
				message.putInt("Interaction", slotIndex);
				message.putInt("Button", mouseButton);
				message.put("Item", heldStack.save(new CompoundTag()));
				this.menu.sendMessage(message);
			}
		}
		
	}

	@Override
	public void receiveMessage(CompoundTag message) {
		if(message.contains("TradeIndex"))
		{
			this.tradeIndex = message.getInt("TradeIndex");
		}
		else if(message.contains("Slot"))
		{
			int slot = message.getInt("Slot");
			if(message.contains("CustomName"))
			{
				this.setCustomName(slot, message.getString("CustomName"));
			}
			else if(message.contains("NewItem"))
			{
				this.setSelectedItem(slot, ItemStack.of(message.getCompound("NewItem")));
			}
		}
		else if(message.contains("NewPrice"))
		{
			CoinValue price = new CoinValue();
			price.load(message, "NewPrice");
			this.setPrice(price);
		}
		else if(message.contains("NewType"))
		{
			this.setType(ItemTradeType.fromIndex(message.getInt("NewType")));
		}
		else if(message.contains("Interaction"))
		{
			int index = message.getInt("Interaction");
			int button = message.getInt("Button");
			this.defaultInteraction(index, ItemStack.of(message.getCompound("Item")), button);
		}
	}
	
}
