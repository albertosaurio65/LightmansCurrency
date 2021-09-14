package io.github.lightman314.lightmanscurrency.containers;

import java.util.ArrayList;
import java.util.List;

import io.github.lightman314.lightmanscurrency.common.ItemTraderStorageUtil;
import io.github.lightman314.lightmanscurrency.containers.interfaces.ICreativeTraderContainer;
import io.github.lightman314.lightmanscurrency.containers.interfaces.IItemEditCapable;
import io.github.lightman314.lightmanscurrency.containers.interfaces.ITraderStorageContainer;
import io.github.lightman314.lightmanscurrency.containers.slots.CoinSlot;
import io.github.lightman314.lightmanscurrency.containers.slots.TradeInputSlot;
import io.github.lightman314.lightmanscurrency.core.ModContainers;
import io.github.lightman314.lightmanscurrency.network.LightmansCurrencyPacketHandler;
import io.github.lightman314.lightmanscurrency.network.message.item_trader.MessageOpenItemEdit;
import io.github.lightman314.lightmanscurrency.network.message.trader.MessageSyncTrades;
import io.github.lightman314.lightmanscurrency.util.InventoryUtil;
import io.github.lightman314.lightmanscurrency.util.MathUtil;
import io.github.lightman314.lightmanscurrency.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.util.MoneyUtil.CoinValue;
import io.github.lightman314.lightmanscurrency.util.TileEntityUtil;
import io.github.lightman314.lightmanscurrency.util.WalletUtil;
import io.github.lightman314.lightmanscurrency.util.WalletUtil.PlayerWallets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.blockentity.ItemTraderBlockEntity;
import io.github.lightman314.lightmanscurrency.ItemTradeData;

public class ItemTraderStorageContainer extends AbstractContainerMenu implements ITraderStorageContainer, ICreativeTraderContainer, IItemEditCapable{

	public static final int SCREEN_EXTENSION = ItemTraderStorageUtil.SCREEN_EXTENSION;
	
	public final Player player;
	
	public final ItemTraderBlockEntity blockEntity;
	
	final Container tradeInventory;
	final Container coinSlots;
	final List<TradeInputSlot> tradeSlots;
	
	public ItemTraderStorageContainer(int windowId, Inventory inventory, ItemTraderBlockEntity blockEntity)
	{
		super(ModContainers.ITEMTRADERSTORAGE, windowId);
		this.blockEntity = blockEntity;
		this.blockEntity.AddContainerListener(this);
		
		this.player = inventory.player;
		
		this.blockEntity.userOpen(this.player);
		
		int tradeCount = this.blockEntity.getTradeCount();
		int rowCount = ItemTraderStorageUtil.getRowCount(tradeCount);
		int columnCount = 9 * ItemTraderStorageUtil.getColumnCount(tradeCount);
		
		//Storage Slots
		for(int y = 0; y < rowCount; y++)
		{
			for(int x = 0; x < columnCount && x + y * columnCount < blockEntity.getStorage().getContainerSize(); x++)
			{
				this.addSlot(new Slot(blockEntity.getStorage(), x + y * columnCount, 8 + x * 18 + SCREEN_EXTENSION + ItemTraderStorageUtil.getStorageSlotOffset(tradeCount, y), 18 + y * 18));
			}
		}
		
		this.tradeInventory = new SimpleContainer(tradeCount);
		this.tradeSlots = new ArrayList<>(tradeCount);
		//Trade Slots
		for(int y = 0; y < tradeInventory.getContainerSize(); y++)
		{
			ItemTradeData trade = blockEntity.getTrade(y);
			TradeInputSlot newSlot = new TradeInputSlot(tradeInventory, y, ItemTraderStorageUtil.getTradeSlotPosX(tradeCount, y), ItemTraderStorageUtil.getTradeSlotPosY(tradeCount, y), trade, this.player);
			this.addSlot(newSlot);
			this.tradeSlots.add(newSlot);
			this.tradeInventory.setItem(y, trade.getSellItem());
		}
		
		int inventoryOffset = ItemTraderStorageUtil.getInventoryOffset(tradeCount);
		
		//Coin slots
		this.coinSlots = new SimpleContainer(5);
		for(int i = 0; i < 5; i++)
		{
			this.addSlot(new CoinSlot(this.coinSlots, i, inventoryOffset + 176 + 8 + SCREEN_EXTENSION, getStorageBottom() + 3 + i * 18));
		}
		
		//Player inventory
		for(int y = 0; y < 3; y++)
		{
			for(int x = 0; x < 9; x++)
			{
				this.addSlot(new Slot(inventory, x + y * 9 + 9, inventoryOffset + 8 + x * 18 + SCREEN_EXTENSION, getStorageBottom() + 15 + y * 18));
			}
		}
		
		//Player hotbar
		for(int x = 0; x < 9; x++)
		{
			this.addSlot(new Slot(inventory, x, inventoryOffset + 8 + x * 18 + SCREEN_EXTENSION, getStorageBottom() + 15 + 58));
		}
		
		
		
		
	}
	
	public int getStorageBottom()
	{
		return (ItemTraderStorageUtil.getRowCount(this.blockEntity.getTradeCount()) * 18) + 28;
	}
	
	public void tick()
	{
		if(this.blockEntity.isRemoved())
		{
			this.player.closeContainer();
			return;
		}
		SyncTrades();
	}
	
	@Override
	public void clicked(int slotId, int dragType, ClickType clickType, Player player)
	{
		
		if(slotClickOverride(slotId, dragType, clickType, player, this.slots, this))
		{
			this.broadcastChanges();
			return;
		}
		
		super.clicked(slotId, dragType, clickType, player);
		
	}
	
	public static boolean slotClickOverride(int slotId, int dragType, ClickType clickType, Player player, List<Slot> inventorySlots, AbstractContainerMenu menu)
	{
		IItemEditCapable itemEditCapability = null;
		if(menu instanceof IItemEditCapable)
			itemEditCapability = (IItemEditCapable)menu;
		//LightmansCurrency.LOGGER.info("ItemTraderStorageContainer.slotClick(" + slotId + ", " + dragType + ", " + clickType + ", " + player.getName().getString() + ")");
		if(slotId > 0 && slotId < inventorySlots.size())
		{
			Slot slot = inventorySlots.get(slotId);
			if(slot instanceof TradeInputSlot && clickType != ClickType.CLONE)
			{
				//LightmansCurrency.LOGGER.info("TradeInputSlot slot clicked at slotID " + slotId);
				if(clickType == ClickType.PICKUP && (dragType == 0 || dragType == 1))
				{
					TradeInputSlot tradeSlot = (TradeInputSlot)slot;
					Container inventory = slot.container;
					int index = slot.getSlotIndex();
					ItemStack tradeStack = inventory.getItem(index);
					ItemStack handStack = menu.getCarried();
					//Remove items from the trade
					if(handStack.isEmpty())
					{
						if(!tradeStack.isEmpty())
						{
							if(dragType == 0)
								inventory.setItem(index, ItemStack.EMPTY);
							else
								inventory.removeItem(index, tradeStack.getCount() / 2);
						}
						else if(itemEditCapability != null)
						{
							//Open the ItemEdit screen
							//LightmansCurrency.LogInfo("Attempting to open the item edit screen for slot index " + slotId);
							itemEditCapability.openItemEditScreenForSlot(slotId);
						}
					}
					//Add items to the trade
					else if(tradeSlot.isTradeItemValid(handStack))
					{
						if(tradeStack.isEmpty())
						{
							//Replace the stack in the inventory
							if(dragType == 0 || handStack.getCount() < 2)
								inventory.setItem(index, handStack.copy());
							else
							{
								ItemStack smallStack = handStack.copy();
								smallStack.setCount(1);
								inventory.setItem(index, smallStack);
							}
							
						}
						else if(InventoryUtil.ItemMatches(handStack, tradeStack) && tradeStack.getCount() < tradeStack.getMaxStackSize())
						{
							//Add to the count
							if(dragType == 0 && handStack.getCount() > 1)
							{
								tradeStack.setCount(MathUtil.clamp(tradeStack.getCount() + handStack.getCount(), 1, tradeStack.getMaxStackSize()));
								inventory.setItem(index, tradeStack);
							}
							else
							{
								tradeStack.grow(1);
								inventory.setItem(index, tradeStack);
							}
						}
						else
						{
							//Override the stack in the inventory
							if(dragType == 0 || handStack.getCount() < 2)
								inventory.setItem(index, handStack.copy());
							else
							{
								ItemStack smallStack = handStack.copy();
								smallStack.setCount(1);
								inventory.setItem(index, smallStack);
							}
						}
					}
				}
				//Otherwise do nothing
				return true;
			}
		}
		return false;
	}
	
	@Override
	public ItemStack quickMoveStack(Player playerEntity, int index)
	{
		
		ItemStack clickedStack = ItemStack.EMPTY;
		
		Slot slot = this.slots.get(index);
		
		if(slot != null && slot.hasItem())
		{
			ItemStack slotStack = slot.getItem();
			clickedStack = slotStack.copy();
			//Merge items from storage back into the players inventory
			if(index < this.blockEntity.getStorage().getContainerSize() + this.tradeInventory.getContainerSize())
			{
				if(!this.moveItemStackTo(slotStack,  this.blockEntity.getStorage().getContainerSize() + this.tradeInventory.getContainerSize() + this.coinSlots.getContainerSize(), this.slots.size(), true))
				{
					return ItemStack.EMPTY;
				}
			}
			//Merge items from the coin slots back into the players inventory
			else if(index < this.blockEntity.getStorage().getContainerSize() + this.tradeInventory.getContainerSize() + this.coinSlots.getContainerSize())
			{
				LightmansCurrency.LogInfo("Merging coin slots back into inventory.");
				if(!this.moveItemStackTo(slotStack, this.blockEntity.getStorage().getContainerSize() + this.tradeInventory.getContainerSize() + this.coinSlots.getContainerSize(), this.slots.size(), true))
				{
					return ItemStack.EMPTY;
				}
			}
			else
			{
				//Merging items from the players inventory
				if(MoneyUtil.isCoin(slotStack))
				{
					//Merge coins into the coin slots
					if(!this.moveItemStackTo(slotStack, this.blockEntity.getStorage().getContainerSize() + this.tradeInventory.getContainerSize(), this.blockEntity.getStorage().getContainerSize() + this.tradeInventory.getContainerSize() + this.coinSlots.getContainerSize(), false))
					{
						return ItemStack.EMPTY;
					}
				}
				//Merge everything else into the storage slots
				else if(!this.moveItemStackTo(slotStack, 0, this.blockEntity.getStorage().getContainerSize(), false))
				{
					return ItemStack.EMPTY;
				}
			}
			if(slotStack.isEmpty())
			{
				slot.set(ItemStack.EMPTY);
			}
			else
			{
				slot.setChanged();
			}
		}
		
		return clickedStack;
		
	}
	
	@Override
	public boolean stillValid(Player playerIn)
	{
		return true;
	}
	
	@Override
	public void removed(Player playerIn)
	{
		
		this.clearContainer(playerIn, this.coinSlots);
		
		this.blockEntity.RemoveContainerListener(this);
		
		super.removed(playerIn);
		
		this.blockEntity.userClose(this.player);
		
	}
	
	public void SyncTrades()
	{
		boolean changed = false;
		boolean isServer = !player.level.isClientSide;
		for(int i = 0; i < blockEntity.getTradeCount(); i++)
		{
			if(!ItemStack.isSameItemSameTags(blockEntity.getTrade(i).getSellItem(), this.tradeInventory.getItem(i)))
			{
				if(isServer)
					blockEntity.getTrade(i).setSellItem(this.tradeInventory.getItem(i));
				changed = true;
			}
		}
		if(changed && isServer)
		{
			//Change detected server-side, so send an update packet to the relevant clients.
			CompoundTag compound = this.blockEntity.writeTrades(new CompoundTag());
			TileEntityUtil.sendUpdatePacket(blockEntity, this.blockEntity.superWrite(compound));
		}
		else if(changed)
		{
			//Change was detected client-side, so inform the server that it needs to check for changes.
			LightmansCurrencyPacketHandler.instance.sendToServer(new MessageSyncTrades());
		}
	}
	
	public void resyncTrades()
	{
		for(int i = 0; i < tradeInventory.getContainerSize(); i++)
		{
			ItemTradeData trade = this.blockEntity.getTrade(i);
			if(trade != null)
			{
				tradeInventory.setItem(i, trade.getSellItem());
				tradeSlots.get(i).updateTrade(trade);
			}
			else
			{
				tradeInventory.setItem(i, ItemStack.EMPTY);
				tradeSlots.get(i).updateTrade(new ItemTradeData());
			}
		}
	}
	
	public boolean isOwner()
	{
		return blockEntity.isOwner(player);
	}
	
	public void openItemEditScreenForSlot(int slotIndex)
	{
		int tradeIndex = slotIndex - this.blockEntity.getStorage().getContainerSize();
		openItemEditScreenForTrade(tradeIndex);
		
	}
	
	public void openItemEditScreenForTrade(int tradeIndex)
	{
		if(this.player.level.isClientSide)
		{
			//LightmansCurrency.LogInfo("Attempting to open item edit container for tradeIndex " + tradeIndex);
			LightmansCurrencyPacketHandler.instance.sendToServer(new MessageOpenItemEdit(tradeIndex));
		}
		else
		{
			//LightmansCurrency.LogInfo("Attempting to open item edit container for tradeIndex " + tradeIndex);
			this.blockEntity.openItemEditMenu(this.player, tradeIndex);
		}
	}
	
	
	
	public void AddCoins()
	{
		if(this.blockEntity.isRemoved())
		{
			this.player.closeContainer();
			return;
		}
		//Get the value of the current 
		CoinValue addValue = CoinValue.easyBuild2(this.coinSlots);
		this.blockEntity.addStoredMoney(addValue);
		this.coinSlots.clearContent();
	}
	
	public boolean HasCoinsToAdd()
	{
		return !coinSlots.isEmpty();
	}
	
	public void CollectCoinStorage()
	{
		if(this.blockEntity.isRemoved())
		{
			this.player.closeContainer();
			return;
		}
		List<ItemStack> coinList = MoneyUtil.getCoinsOfValue(blockEntity.getStoredMoney());
		PlayerWallets wallet = WalletUtil.getWallets(this.player);
		if(!wallet.hasWallet())
		{
			List<ItemStack> spareCoins = new ArrayList<>();
			for(int i = 0; i < coinList.size(); i++)
			{
				ItemStack extraCoins = wallet.PlaceCoin(coinList.get(i));
				if(!extraCoins.isEmpty())
					spareCoins.add(extraCoins);
			}
			coinList = spareCoins;
		}
		Container inventory = new SimpleContainer(coinList.size());
		for(int i = 0; i < coinList.size(); i++)
		{
			inventory.setItem(i, coinList.get(i));
		}
		this.clearContainer(player, inventory);
		
		//Clear the coin storage
		blockEntity.clearStoredMoney();
		
	}
	
	public void ToggleCreative()
	{
		if(this.blockEntity.isRemoved())
		{
			this.player.closeContainer();
			return;
		}
		this.blockEntity.toggleCreative();
	}

	@Override
	public void AddTrade() {
		this.blockEntity.addTrade();
	}

	@Override
	public void RemoveTrade() {
		this.blockEntity.removeTrade();
	}
	
}
