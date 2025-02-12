package io.github.lightman314.lightmanscurrency.common.traders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import io.github.lightman314.lightmanscurrency.blockentity.handler.ICanCopy;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount.AccountReference;
import io.github.lightman314.lightmanscurrency.common.capability.WalletCapability;
import io.github.lightman314.lightmanscurrency.common.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.core.ModItems;
import io.github.lightman314.lightmanscurrency.items.TicketItem;
import io.github.lightman314.lightmanscurrency.items.WalletItem;
import io.github.lightman314.lightmanscurrency.menus.slots.InteractionSlot;
import io.github.lightman314.lightmanscurrency.money.CoinValue;
import io.github.lightman314.lightmanscurrency.money.MoneyUtil;
import io.github.lightman314.lightmanscurrency.util.InventoryUtil;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

@SuppressWarnings("removal")
public class TradeContext {
	
	public enum TradeResult {
		/**
		 * Remote trade was successfully executed
		 */
		SUCCESS(null),
		/**
		 * Trade failed as the trader is out of stock
		 */
		FAIL_OUT_OF_STOCK("lightmanscurrency.remotetrade.fail.nostock"),
		
		/**
		 * Trade failed as the player could not afford the trade
		 */
		FAIL_CANNOT_AFFORD("lightmanscurrency.remotetrade.fail.cantafford"),
		/**
		 * Trade failed as there's no room for the output items
		 */
		FAIL_NO_OUTPUT_SPACE("lightmanscurrency.remotetrade.fail.nospace.output"),
		/**
		 * Trade failed as there's no room for the input items
		 */
		FAIL_NO_INPUT_SPACE("lightmanscurrency.remotetrade.fail.nospace.input"),
		/**
		 * Trade failed as the trade rules denied the trade
		 */
		FAIL_TRADE_RULE_DENIAL("lightmanscurrency.remotetrade.fail.traderule"),
		/**
		 * Trade failed as the trade is no longer valid
		 */
		FAIL_INVALID_TRADE("lightmanscurrency.remotetrade.fail.invalid"),
		/**
		 * Trade failed as this trader does not support remote trades
		 */
		FAIL_NOT_SUPPORTED("lightmanscurrency.remotetrade.fail.notsupported"),
		/**
		 * Trade failed as the trader was null
		 */
		FAIL_NULL("lightmanscurrency.remotetrade.fail.null");
		public boolean hasMessage() { return this.failMessage != null; }
		public final Component failMessage;
		TradeResult(String message) { this.failMessage = message == null ? null : Component.translatable(message); }
	}
	
	public final boolean isStorageMode;
	
	//Trader Data (public as it will be needed for trade data context)
	private final TraderData trader;
	public boolean hasTrader() { return this.trader != null; }
	public TraderData getTrader() { return this.trader; }
	
	//Player Data
	private final Player player;
	public boolean hasPlayer() { return this.player != null; }
	public Player getPlayer() { return this.player; }
	
	//Public as it will be needed to run trade events to confirm a trades alerts/cost for display purposes
	private final PlayerReference playerReference;
	public boolean hasPlayerReference() { return this.playerReference != null; }
	public final PlayerReference getPlayerReference() { return this.playerReference; }
	
	//Money/Payment related data
	private final AccountReference bankAccount;
	private boolean hasBankAccount() { return this.bankAccount != null && this.bankAccount.get() != null; }
	
	private final Container coinSlots;
	private boolean hasCoinSlots() { return this.hasPlayer() && this.coinSlots != null; }
	
	private final CoinValue storedMoney;
	private boolean hasStoredMoney() { return this.storedMoney != null; }
	
	private final BiConsumer<CoinValue,Boolean> moneyListener;
	
	//Interaction Slots (bucket/battery slot, etc.)
	private final InteractionSlot interactionSlot;
	private boolean hasInteractionSlot(String type) { return this.getInteractionSlot(type) != null; }
	private InteractionSlot getInteractionSlot(String type) { if(this.interactionSlot == null) return null; if(this.interactionSlot.isType(type)) return this.interactionSlot; return null; }
	
	//Item related data
	private final IItemHandler itemHandler;
	private boolean hasItemHandler() { return this.itemHandler != null; }
	
	//Fluid related data
	private final IFluidHandler fluidTank;
	private boolean hasFluidTank() { return this.fluidTank != null; }
	
	//Energy related data
	private final IEnergyStorage energyTank;
	private boolean hasEnergyTank() { return this.energyTank != null; }
	
	private TradeContext(Builder builder) {
		this.isStorageMode = builder.storageMode;
		this.trader = builder.trader;
		this.player = builder.player;
		this.playerReference = builder.playerReference;
		this.bankAccount = builder.bankAccount;
		this.coinSlots = builder.coinSlots;
		this.storedMoney = builder.storedCoins;
		this.moneyListener = builder.moneyListener;
		this.interactionSlot = builder.interactionSlot;
		this.itemHandler = builder.itemHandler;
		this.fluidTank = builder.fluidHandler;
		this.energyTank = builder.energyHandler;
	}
	
	public boolean hasPaymentMethod() { return this.hasPlayer() || this.hasCoinSlots() || this.hasBankAccount() || this.hasStoredMoney(); }
	
	public boolean hasFunds(CoinValue price)
	{
		return this.getAvailableFunds() >= price.getRawValue();
	}
	
	public long getAvailableFunds() {
		long funds = 0;
		if(this.hasBankAccount())
			funds += this.bankAccount.get().getCoinStorage().getRawValue();
		if(this.hasPlayer())
		{
			AtomicLong walletFunds = new AtomicLong(0);
			WalletCapability.getWalletHandler(this.player).ifPresent(walletHandler ->{
				ItemStack wallet = walletHandler.getWallet();
				if(WalletItem.isWallet(wallet.getItem()))
					walletFunds.set(MoneyUtil.getValue(WalletItem.getWalletInventory(wallet)));
			});
			funds += walletFunds.get();
		}
		if(this.hasStoredMoney())
			funds += this.storedMoney.getRawValue();
		if(this.hasCoinSlots() && this.hasPlayer())
			funds += MoneyUtil.getValue(this.coinSlots);
		return funds;
	}
	
	public boolean getPayment(CoinValue price)
	{
		if(this.hasFunds(price))
		{
			if(this.moneyListener != null)
				this.moneyListener.accept(price, false);
			long amountToWithdraw = price.getRawValue();
			if(this.hasCoinSlots() && this.hasPlayer())
			{
				amountToWithdraw = MoneyUtil.takeObjectsOfValue(amountToWithdraw, this.coinSlots, true);
				if(amountToWithdraw < 0)
				{
					List<ItemStack> change = MoneyUtil.getCoinsOfValue(-amountToWithdraw);
					for(ItemStack stack : change)
					{
						ItemStack c = InventoryUtil.TryPutItemStack(this.coinSlots, stack);
						if(!c.isEmpty())
							ItemHandlerHelper.giveItemToPlayer(this.player, c);
					}
				}
			}
			if(this.hasStoredMoney() && amountToWithdraw > 0)
			{
				long removeAmount = Math.min(amountToWithdraw, this.storedMoney.getRawValue());
				amountToWithdraw -= removeAmount;
				storedMoney.loadFromOldValue(storedMoney.getRawValue() - removeAmount);
			}
			if(this.hasBankAccount() && amountToWithdraw > 0)
			{
				CoinValue withdrawAmount = this.bankAccount.get().withdrawCoins(new CoinValue(amountToWithdraw));
				amountToWithdraw -= withdrawAmount.getRawValue();
				if(this.hasTrader() && withdrawAmount.getRawValue() > 0)
				{
					this.bankAccount.get().LogInteraction(this.getTrader(), withdrawAmount, false);
				}
			}
			if(this.hasPlayer() && amountToWithdraw > 0)
			{
				AtomicLong withdrawAmount = new AtomicLong(amountToWithdraw);
				WalletCapability.getWalletHandler(this.player).ifPresent(walletHandler ->{
					ItemStack wallet = walletHandler.getWallet();
					if(WalletItem.isWallet(wallet.getItem()))
					{
						NonNullList<ItemStack> walletInventory = WalletItem.getWalletInventory(wallet);
						long change = MoneyUtil.takeObjectsOfValue(withdrawAmount.get(), walletInventory, true);
						WalletItem.putWalletInventory(wallet, walletInventory);
						if(change < 0)
						{
							for(ItemStack stack : MoneyUtil.getCoinsOfValue(-change))
							{
								ItemStack c = WalletItem.PickupCoin(wallet, stack);
								if(!c.isEmpty())
									ItemHandlerHelper.giveItemToPlayer(this.player, c);
							}
							change = 0;
						}
						withdrawAmount.set(change);
					}
				});
				amountToWithdraw = withdrawAmount.get();
			}
			return true;
		}
		return false;
	}
	
	public boolean givePayment(CoinValue price)
	{
		if(this.moneyListener != null)
			this.moneyListener.accept(price, true);
		if(this.hasBankAccount())
		{
			this.bankAccount.get().depositCoins(price);
			if(this.hasTrader())
				this.bankAccount.get().LogInteraction(this.getTrader(), price, true);
			return true;
		}
		else if(this.hasStoredMoney())
		{
			this.storedMoney.addValue(price);
			return true;
		}
		else if(this.hasPlayer())
		{
			List<ItemStack> coins = MoneyUtil.getCoinsOfValue(price);
			AtomicReference<List<ItemStack>> change = new AtomicReference<>(coins);
			WalletCapability.getWalletHandler(this.player).ifPresent(walletHandler ->{
				ItemStack wallet = walletHandler.getWallet();
				if(WalletItem.isWallet(wallet.getItem()))
				{
					change.set(new ArrayList<>());
					for(int i = 0; i < coins.size(); ++i)
					{
						ItemStack coin = WalletItem.PickupCoin(wallet, coins.get(i));
						if(!coin.isEmpty())
							change.get().add(coin);
					}
				}
			});
			if(this.hasCoinSlots() && change.get().size() > 0)
			{
				for(int i = 0; i < change.get().size(); ++i)
				{
					ItemStack remainder = InventoryUtil.TryPutItemStack(this.coinSlots, change.get().get(i));
					if(!remainder.isEmpty())
						ItemHandlerHelper.giveItemToPlayer(this.player, remainder);
				}
			}
			else if(change.get().size() > 0)
			{
				for(int i = 0; i < change.get().size(); ++i)
				{
					ItemHandlerHelper.giveItemToPlayer(this.player, change.get().get(i));
				}
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Whether the given item stack is present in the item handler, and can be successfully removed without issue.
	 */
	public boolean hasItem(ItemStack item)
	{
		if(this.hasItemHandler())
			return InventoryUtil.CanExtractItem(this.itemHandler, item);
		else if(this.hasPlayer())
			return InventoryUtil.GetItemCount(this.player.getInventory(), item) >= item.getCount();
		return false;
	}
	
	/**
	 * Whether the given item stacks are present in the item handler, and can be successfully removed without issue.
	 */
	public boolean hasItems(ItemStack... items)
	{
		for(ItemStack item : InventoryUtil.combineQueryItems(items))
		{
			if(!hasItem(item))
				return false;
		}
		return true;
	}
	
	/**
	 * Whether a ticket with the given ticket id is present in the item handler, and can be successfully removed without issue.
	 */
	public boolean hasTicket(UUID ticketID) {
		if(this.hasItemHandler())
		{
			for(int i = 0; i < this.itemHandler.getSlots(); ++i)
			{
				ItemStack stack = this.itemHandler.getStackInSlot(i);
				if(stack.getItem() == ModItems.TICKET.get())
				{
					UUID id = TicketItem.GetTicketID(stack);
					if(id != null && id.equals(ticketID))
					{
						ItemStack copyStack = stack.copy();
						copyStack.setCount(1);
						if(InventoryUtil.CanExtractItem(this.itemHandler, copyStack))
							return true;
					}
				}
			}
		}
		else if(this.hasPlayer())
		{
			Inventory inventory = this.player.getInventory();
			for(int i = 0; i < inventory.getContainerSize(); ++i)
			{
				ItemStack stack = inventory.getItem(i);
				if(stack.getItem() == ModItems.TICKET.get())
				{
					UUID id = TicketItem.GetTicketID(stack);
					if(id != null && id.equals(ticketID))
						return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Removes the given item stack from the item handler.
	 * @return Whether the extraction was successful. Will return false if it could not be extracted correctly.
	 */
	public boolean collectItem(ItemStack item)
	{
		if(this.hasItem(item))
		{
			if(this.hasItemHandler())
			{
				InventoryUtil.RemoveItemCount(this.itemHandler, item);
				return true;
			}
			else if(this.hasPlayer())
			{
				InventoryUtil.RemoveItemCount(this.player.getInventory(), item);
				return true;
			}
		}
		return false;
	}
	
	
	/**
	 * Removes the given ticket from the item handler.
	 * @return Whether the extraction was successful. Will return false if it could not be extracted correctly.
	 * 
	 */
	public boolean collectTicket(UUID ticketID) {
		if(this.hasTicket(ticketID))
		{
			if(this.hasItemHandler())
			{
				for(int i = 0; i < this.itemHandler.getSlots(); ++i) {
					ItemStack stack = this.itemHandler.getStackInSlot(i);
					if(stack.getItem() == ModItems.TICKET.get())
					{
						UUID id = TicketItem.GetTicketID(stack);
						if(id != null && id.equals(ticketID))
						{
							ItemStack extractStack = stack.copy();
							extractStack.setCount(1);
							if(InventoryUtil.RemoveItemCount(this.itemHandler, extractStack))
								return true;
						}
					}
				}
			}
			else if(this.hasPlayer())
			{
				Inventory inventory = this.player.getInventory();
				for(int i = 0; i < inventory.getContainerSize(); ++i)
				{
					ItemStack stack = inventory.getItem(i);
					if(stack.getItem() == ModItems.TICKET.get())
					{
						UUID id = TicketItem.GetTicketID(stack);
						if(id != null && id.equals(ticketID))
						{
							inventory.removeItem(i, 1);
							inventory.setChanged();
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	public boolean canFitItem(ItemStack item)
	{
		if(item.isEmpty())
			return true;
		if(this.hasItemHandler())
			return ItemHandlerHelper.insertItemStacked(this.itemHandler, item, true).isEmpty();
		if(this.hasPlayer())
			return true;
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public boolean canFitItems(ItemStack... items)
	{
		if(this.hasItemHandler())
		{
			IItemHandler original = this.itemHandler;
			IItemHandler copy = null;
			if(original instanceof ICanCopy<?>)
			{
				copy = ((ICanCopy<? extends IItemHandler>)original).copy();
			}
			else
			{
				//Assume a default item handler
				NonNullList<ItemStack> inventory = NonNullList.withSize(original.getSlots(), ItemStack.EMPTY);
				for(int i = 0; i < original.getSlots(); ++i)
					inventory.set(i, original.getStackInSlot(i));
				copy = new ItemStackHandler(inventory);
			}
			for(ItemStack item : items)
			{
				if(!ItemHandlerHelper.insertItemStacked(copy, item, false).isEmpty())
					return false;
			}
			return true;
		}
		if(this.hasPlayer())
			return true;
		return false;
	}
	
	public boolean putItem(ItemStack item)
	{
		if(this.canFitItem(item))
		{
			if(this.hasItemHandler())
			{
				ItemStack leftovers = ItemHandlerHelper.insertItemStacked(this.itemHandler, item, false);
				if(leftovers.isEmpty())
					return true;
				else
				{
					//Failed to place the items in the item handler, so take what few were placed back.
					ItemStack placedStack = item.copy();
					placedStack.setCount(item.getCount() - leftovers.getCount());
					if(!item.isEmpty())
						this.collectItem(placedStack);
					return false;
				}	
			}
			if(this.hasPlayer())
			{
				ItemHandlerHelper.giveItemToPlayer(this.player, item);
				return true;
			}
		}
		return false;
	}
	
	public boolean hasFluid(FluidStack fluid)
	{
		if(this.hasFluidTank())
		{
			FluidStack result = this.fluidTank.drain(fluid, FluidAction.SIMULATE);
			if(result.isEmpty() || result.getAmount() < fluid.getAmount())
				return false;
			return true;
		}
		if(this.hasInteractionSlot(InteractionSlotData.FLUID_TYPE))
		{
			ItemStack bucketStack = this.getInteractionSlot(InteractionSlotData.FLUID_TYPE).getItem();
			AtomicBoolean hasFluid = new AtomicBoolean(false);
			FluidUtil.getFluidHandler(bucketStack).ifPresent(fluidHandler ->{
				FluidStack result = fluidHandler.drain(fluid, FluidAction.SIMULATE);
				hasFluid.set(!result.isEmpty() && result.getAmount() == fluid.getAmount());
			});
			return hasFluid.get();
		}
		return false;
	}
	
	public boolean drainFluid(FluidStack fluid)
	{
		if(this.hasFluid(fluid))
		{
			if(this.hasFluidTank())
			{
				this.fluidTank.drain(fluid, FluidAction.EXECUTE);
				return true;
			}
			if(this.hasInteractionSlot(InteractionSlotData.FLUID_TYPE))
			{
				InteractionSlot slot = this.getInteractionSlot(InteractionSlotData.FLUID_TYPE);
				ItemStack bucketStack = slot.getItem();
				FluidUtil.getFluidHandler(bucketStack).ifPresent(fluidHandler ->{
					fluidHandler.drain(fluid, FluidAction.EXECUTE);
					slot.set(fluidHandler.getContainer());
				});
				return true;
			}
		}
		return false;
	}
	
	public boolean canFitFluid(FluidStack fluid)
	{
		if(this.hasFluidTank())
			return this.fluidTank.fill(fluid, FluidAction.SIMULATE) == fluid.getAmount();
		if(this.hasInteractionSlot(InteractionSlotData.FLUID_TYPE))
		{
			ItemStack bucketStack = this.getInteractionSlot(InteractionSlotData.FLUID_TYPE).getItem();
			AtomicBoolean fitFluid = new AtomicBoolean(false);
			FluidUtil.getFluidHandler(bucketStack).ifPresent(fluidHandler ->{
				fitFluid.set(fluidHandler.fill(fluid, FluidAction.SIMULATE) == fluid.getAmount());
			});
			return fitFluid.get();
		}
		return false;
	}
	
	public boolean fillFluid(FluidStack fluid)
	{
		if(this.canFitFluid(fluid))
		{
			if(this.hasFluidTank())
			{
				this.fluidTank.fill(fluid, FluidAction.EXECUTE);
				return true;
			}
			if(this.hasInteractionSlot(InteractionSlotData.FLUID_TYPE))
			{
				InteractionSlot slot = this.getInteractionSlot(InteractionSlotData.FLUID_TYPE);
				ItemStack bucketStack = slot.getItem();
				FluidUtil.getFluidHandler(bucketStack).ifPresent(fluidHandler ->{
					fluidHandler.fill(fluid, FluidAction.EXECUTE);
					//Put the modified item back into the slot
					slot.set(fluidHandler.getContainer());
				});
			}
		}
		return false;
	}
	
	public boolean hasEnergy(int amount)
	{
		if(this.hasEnergyTank())
			return this.energyTank.extractEnergy(amount, true) == amount;
		else if(this.hasInteractionSlot(InteractionSlotData.ENERGY_TYPE))
		{
			ItemStack batteryStack = this.getInteractionSlot(InteractionSlotData.ENERGY_TYPE).getItem();
			AtomicBoolean hasEnergy = new AtomicBoolean(false);
			batteryStack.getCapability(CapabilityEnergy.ENERGY).ifPresent(energyHandler ->{
				hasEnergy.set(energyHandler.extractEnergy(amount, true) == amount);
			});
			return hasEnergy.get();
		}
		return false;
	}
	
	public boolean drainEnergy(int amount)
	{
		if(this.hasEnergy(amount))
		{
			if(this.hasEnergyTank())
			{
				this.energyTank.extractEnergy(amount, false);
				return true;
			}
			if(this.hasInteractionSlot(InteractionSlotData.ENERGY_TYPE))
			{
				ItemStack batteryStack = this.getInteractionSlot(InteractionSlotData.ENERGY_TYPE).getItem();
				batteryStack.getCapability(CapabilityEnergy.ENERGY).ifPresent(energyHandler ->{
					energyHandler.extractEnergy(amount, false);
				});
				return true;
			}
		}
		return false;
	}
	
	public boolean canFitEnergy(int amount)
	{
		if(this.hasEnergyTank())
			return this.energyTank.receiveEnergy(amount, true) == amount;
		else if(this.hasInteractionSlot(InteractionSlotData.ENERGY_TYPE))
		{
			ItemStack batteryStack = this.getInteractionSlot(InteractionSlotData.ENERGY_TYPE).getItem();
			AtomicBoolean fitsEnergy = new AtomicBoolean(false);
			batteryStack.getCapability(CapabilityEnergy.ENERGY).ifPresent(energyHandler ->{
				fitsEnergy.set(energyHandler.receiveEnergy(amount, true) == amount);
			});
			return fitsEnergy.get();
		}
		return false;
	}
	
	public boolean fillEnergy(int amount)
	{
		if(this.canFitEnergy(amount))
		{
			if(this.hasEnergyTank())
			{
				this.energyTank.receiveEnergy(amount, false);
				return true;
			}
			else if(this.hasInteractionSlot(InteractionSlotData.ENERGY_TYPE))
			{
				ItemStack batteryStack = this.getInteractionSlot(InteractionSlotData.ENERGY_TYPE).getItem();
				batteryStack.getCapability(CapabilityEnergy.ENERGY).ifPresent(energyHandler ->{
					energyHandler.receiveEnergy(amount, false);
				});
				return true;
			}
		}
		return false;
	}
	
	public static TradeContext createStorageMode(TraderData trader) { return new Builder(trader).build(); }
	public static Builder create(TraderData trader, Player player) { return new Builder(trader, player); }
	public static Builder create(TraderData trader, PlayerReference player) { return new Builder(trader, player); }
	
	public static class Builder
	{
		
		//Core
		private final boolean storageMode;
		private final TraderData trader;
		private final Player player;
		private final PlayerReference playerReference;
		
		//Money
		private AccountReference bankAccount;
		private Container coinSlots;
		private CoinValue storedCoins;
		private BiConsumer<CoinValue,Boolean> moneyListener;
		
		//Interaction Slots
		private InteractionSlot interactionSlot;
		
		//Item
		private IItemHandler itemHandler;
		//Fluid
		private IFluidHandler fluidHandler;
		//Energy
		private IEnergyStorage energyHandler;
		
		private Builder(TraderData trader) { this.storageMode = true; this.trader = trader; this.player = null; this.playerReference = null; }
		private Builder(TraderData trader, Player player) { this.trader = trader; this.player = player; this.playerReference = PlayerReference.of(player); this.storageMode = false; }
		private Builder(TraderData trader, PlayerReference player) { this.trader = trader; this.playerReference = player; this.player = null; this.storageMode = false; }
		
		public Builder withBankAccount(AccountReference bankAccount) { this.bankAccount = bankAccount; return this; }
		public Builder withCoinSlots(Container coinSlots) { this.coinSlots = coinSlots; return this; }
		public Builder withStoredCoins(CoinValue storedCoins) { this.storedCoins = storedCoins; return this; }
		
		public Builder withMoneyListener(BiConsumer<CoinValue,Boolean> moneyListener) { this.moneyListener = moneyListener; return this; }
		
		public Builder withInteractionSlot(InteractionSlot interactionSlot) { this.interactionSlot = interactionSlot; return this; }
		
		public Builder withItemHandler(IItemHandler itemHandler) { this.itemHandler = itemHandler; return this; }
		public Builder withFluidHandler(IFluidHandler fluidHandler) { this.fluidHandler = fluidHandler; return this; }
		public Builder withEnergyHandler(IEnergyStorage energyHandler) { this.energyHandler = energyHandler; return this; }
		
		
		public TradeContext build() { return new TradeContext(this); }
		
	}
	
}
