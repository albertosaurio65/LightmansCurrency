package io.github.lightman314.lightmanscurrency.common.bank;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.lightman314.lightmanscurrency.client.data.ClientBankData;
import io.github.lightman314.lightmanscurrency.commands.CommandLCAdmin;
import io.github.lightman314.lightmanscurrency.common.notifications.Notification;
import io.github.lightman314.lightmanscurrency.common.notifications.NotificationData;
import io.github.lightman314.lightmanscurrency.common.notifications.NotificationSaveData;
import io.github.lightman314.lightmanscurrency.common.notifications.types.bank.BankTransferNotification;
import io.github.lightman314.lightmanscurrency.common.notifications.types.bank.DepositWithdrawNotification;
import io.github.lightman314.lightmanscurrency.common.notifications.types.bank.LowBalanceNotification;
import io.github.lightman314.lightmanscurrency.common.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import io.github.lightman314.lightmanscurrency.common.teams.TeamSaveData;
import io.github.lightman314.lightmanscurrency.common.traders.TraderData;
import io.github.lightman314.lightmanscurrency.money.CoinValue;
import io.github.lightman314.lightmanscurrency.money.MoneyUtil;
import io.github.lightman314.lightmanscurrency.util.InventoryUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.NonNullSupplier;

public class BankAccount {
	
	public enum AccountType { Player(0), Team(1);
		
		public final int id;
		
		private AccountType(int id) { this.id = id; }
		
		public static final AccountType fromID(int id) {
			for(AccountType type : AccountType.values())
				if(type.id == id)
					return type;
			return AccountType.Player;
		}
		
	}
	
	private final IMarkDirty markDirty;
	
	private CoinValue coinStorage = new CoinValue();
	public CoinValue getCoinStorage() { return this.coinStorage; }
	
	private CoinValue notificationLevel = new CoinValue();
	public CoinValue getNotificationValue() { return this.notificationLevel; }
	public long getNotificationLevel() { return this.notificationLevel.getRawValue(); }
	public void setNotificationValue(CoinValue value) { this.notificationLevel = value.copy(); this.markDirty(); }
	
	private Consumer<NonNullSupplier<Notification>> notificationSender;
	public void setNotificationConsumer(Consumer<NonNullSupplier<Notification>> notificationSender) { this.notificationSender = notificationSender; }
	public void pushLocalNotification(Notification notification) {
		this.logger.addNotification(notification);
		this.markDirty();
	}
	public void pushNotification(NonNullSupplier<Notification> notification) {
		this.pushLocalNotification(notification.get());
		if(this.notificationSender != null)
			this.notificationSender.accept(notification);
	}
	
	public static Consumer<NonNullSupplier<Notification>> generateNotificationAcceptor(UUID playerID) {
		return (notification) -> {
			NotificationSaveData.PushNotification(playerID, notification.get());
		};
	}
	
	private NotificationData logger = new NotificationData();
	public List<Notification> getNotifications() { return this.logger.getNotifications(); }
	
	private String ownerName = "Unknown";
	public String getOwnersName() { return this.ownerName; }
	public void updateOwnersName(String ownerName) { this.ownerName = ownerName; }
	public MutableComponent getName() { return Component.translatable("lightmanscurrency.bankaccount", this.ownerName); }
	
	public void depositCoins(CoinValue depositAmount) {
		this.coinStorage = new CoinValue(this.coinStorage.getRawValue() + depositAmount.getRawValue());
		this.markDirty();
	}
	
	public CoinValue withdrawCoins(CoinValue withdrawAmount) {
		long oldValue = this.coinStorage.getRawValue();
		if(withdrawAmount.getRawValue() > this.coinStorage.getRawValue())
			withdrawAmount = this.coinStorage.copy();
		//Cannot withdraw no money
		if(withdrawAmount.getRawValue() <= 0)
			return CoinValue.EMPTY;
		this.coinStorage.loadFromOldValue(this.coinStorage.getRawValue() - withdrawAmount.getRawValue());
		this.markDirty();
		//Check if we should push the notification
		if(oldValue >= this.getNotificationLevel() && this.coinStorage.getRawValue() < this.getNotificationLevel())
			this.pushNotification(() -> new LowBalanceNotification(this.getName(), this.notificationLevel));
		return withdrawAmount;
	}
	
	public void LogInteraction(Player player, CoinValue amount, boolean isDeposit) {
		this.pushLocalNotification(new DepositWithdrawNotification.Player(PlayerReference.of(player), this.getName(), isDeposit, amount));
		this.markDirty();
	}
	
	public void LogInteraction(TraderData trader, CoinValue amount, boolean isDeposit) {
		this.pushLocalNotification(new DepositWithdrawNotification.Trader(trader.getName(), this.getName(), isDeposit, amount));
		this.markDirty();
	}
	
	public void LogTransfer(Player player, CoinValue amount, MutableComponent otherAccount, boolean wasReceived) {
		this.pushLocalNotification(new BankTransferNotification(PlayerReference.of(player), amount, this.getName(), otherAccount, wasReceived));
		this.markDirty();
	}
	
	public static void DepositCoins(IBankAccountMenu menu, CoinValue amount)
	{
		if(menu == null)
			return;
		DepositCoins(menu.getPlayer(), menu.getCoinInput(), menu.getBankAccount(), amount);
	}
	
	public static void DepositCoins(Player player, Container coinInput, BankAccount account, CoinValue amount)
	{
		if(account == null)
			return;
		
		CoinValue actualAmount = MoneyUtil.getCoinValue(coinInput);
		//If amount is not defined, or the amount is more than the amount available, set the amount to deposit to the actual amount
		if(amount.getRawValue() > actualAmount.getRawValue() || amount.getRawValue() <= 0)
			amount = actualAmount;
		//Handle deposit removal the same as a payment
		MoneyUtil.ProcessPayment(coinInput, player, amount, true);
		//Add the deposit amount to the account
		account.depositCoins(amount);
		account.LogInteraction(player, amount, true);
		
	}
	
	public static void WithdrawCoins(IBankAccountMenu menu, CoinValue amount)
	{
		if(menu == null)
			return;
		WithdrawCoins(menu.getPlayer(), menu.getCoinInput(), menu.getBankAccount(), amount);
	}
	
	public static void WithdrawCoins(Player player, Container coinOutput, BankAccount account, CoinValue amount)
	{
		if(account == null || amount.getRawValue() <= 0)
			return;
		
		CoinValue withdrawnAmount = account.withdrawCoins(amount);
		
		List<ItemStack> coins = MoneyUtil.getCoinsOfValue(withdrawnAmount);
		//Attempt to fill the coins into the coin slots
		for(int i = 0; i < coins.size(); ++i)
		{
			ItemStack remainder = InventoryUtil.TryPutItemStack(coinOutput, coins.get(i));
			if(!remainder.isEmpty())
			{
				//Attempt to give it to the player directly
				if(!player.addItem(remainder))
				{
					//Drop the remainder on the ground
					InventoryUtil.dumpContents(player.level, player.blockPosition(), remainder);
				}
			}
		}
		account.LogInteraction(player, withdrawnAmount, false);
	}
	
	public static MutableComponent TransferCoins(IBankAccountAdvancedMenu menu, CoinValue amount, AccountReference destination)
	{
		return TransferCoins(menu.getPlayer(), menu.getBankAccount(), amount, destination.get());
	}
	
	public static MutableComponent TransferCoins(Player player, BankAccount fromAccount, CoinValue amount, BankAccount destinationAccount)
	{
		if(fromAccount == null)
			return Component.translatable("gui.bank.transfer.error.null.from");
		if(destinationAccount == null)
			return Component.translatable("gui.bank.transfer.error.null.to");
		if(amount.getRawValue() <= 0)
			return Component.translatable("gui.bank.transfer.error.amount", amount.getString("nothing"));
		if(fromAccount == destinationAccount)
			return Component.translatable("gui.bank.transfer.error.same");
		
		CoinValue withdrawnAmount = fromAccount.withdrawCoins(amount);
		if(withdrawnAmount.getRawValue() <= 0)
			return Component.translatable("gui.bank.transfer.error.nobalance", amount.getString());
		
		destinationAccount.depositCoins(withdrawnAmount);
		fromAccount.LogTransfer(player, withdrawnAmount, destinationAccount.getName(), false);
		destinationAccount.LogTransfer(player, withdrawnAmount, fromAccount.getName(), true);
		
		return Component.translatable("gui.bank.transfer.success", withdrawnAmount.getString(), destinationAccount.getName());
		
	}
	
	public BankAccount() { this((IMarkDirty)null); }
	public BankAccount(IMarkDirty markDirty) { this.markDirty = markDirty; }
	
	public BankAccount(CompoundTag compound) { this(null, compound); }
	public BankAccount(IMarkDirty markDirty, CompoundTag compound) {
		this.markDirty = markDirty;
		this.coinStorage.load(compound, "CoinStorage");
		this.logger.load(compound.getCompound("AccountLogs"));
		this.ownerName = compound.getString("OwnerName");
		this.notificationLevel.load(compound, "NotificationLevel");
	}
	
	public void markDirty()
	{
		if(this.markDirty != null)
			this.markDirty.markDirty();
	}
	
	public final CompoundTag save() {
		CompoundTag compound = new CompoundTag();
		this.coinStorage.save(compound, "CoinStorage");
		compound.put("AccountLogs", this.logger.save());
		compound.putString("OwnerName", this.ownerName);
		this.notificationLevel.save(compound, "NotificationLevel");
		return compound;
	}
	
	public static AccountReference GenerateReference(Player player) { return GenerateReference(player.level.isClientSide, player.getUUID()); }
	public static AccountReference GenerateReference(boolean isClient, UUID playerID) { return new AccountReference(isClient, playerID); }
	public static AccountReference GenerateReference(boolean isClient, PlayerReference player) { return GenerateReference(isClient, player.id); }
	public static AccountReference GenerateReference(boolean isClient, Team team) { return GenerateReference(isClient, team.getID()); }
	public static AccountReference GenerateReference(boolean isClient, long teamID) { return new AccountReference(isClient, teamID); }
	
	
	public static AccountReference LoadReference(boolean isClient, CompoundTag compound) {
		if(compound.contains("PlayerID"))
		{
			UUID id = compound.getUUID("PlayerID");
			return GenerateReference(isClient, id);
		}
		if(compound.contains("TeamID"))
		{
			long id = compound.getLong("TeamID");
			return GenerateReference(isClient, id);
		}
		return null;
	}
	
	public static AccountReference LoadReference(boolean isClient, FriendlyByteBuf buffer) {
		try {
			AccountType accountType = AccountType.fromID(buffer.readInt());
			if(accountType == AccountType.Player)
			{
				UUID id = buffer.readUUID();
				return GenerateReference(isClient, id);
			}
			if(accountType == AccountType.Team)
			{
				long id = buffer.readLong();
				return GenerateReference(isClient, id);
			}
			return null;
		} catch(Exception e) { e.printStackTrace(); return null; }
	}
	
	public static class AccountReference {
		
		private final boolean isClient;
		public final AccountType accountType;
		public final UUID playerID;
		public final long teamID;
		
		private AccountReference(boolean isClient, UUID playerID) { this.isClient = isClient; this.accountType = AccountType.Player; this.playerID = playerID; this.teamID = -1; }
		
		private AccountReference(boolean isClient, long teamID) { this.isClient = isClient; this.accountType = AccountType.Team; this.teamID = teamID; this.playerID = null; }
		
		public CompoundTag save() {
			CompoundTag compound = new CompoundTag();
			//compound.putInt("Type", this.accountType.id);
			if(this.playerID != null)
				compound.putUUID("PlayerID", this.playerID);
			if(this.teamID >= 0)
				compound.putLong("TeamID", this.teamID);
			return compound;
		}
		
		public void writeToBuffer(FriendlyByteBuf buffer) {
			buffer.writeInt(this.accountType.id);
			if(this.playerID != null)
				buffer.writeUUID(this.playerID);
			if(this.teamID >= 0)
				buffer.writeLong(this.teamID);
		}
		
		public BankAccount get() {
			switch(this.accountType) {
			case Player:
				return BankSaveData.GetBankAccount(this.isClient, this.playerID);
			case Team:
				Team team = TeamSaveData.GetTeam(this.isClient, this.teamID);
				if(team != null && team.hasBankAccount())
					return team.getBankAccount();
			default:
				return null;
			}
		}
		
		public boolean allowedAccess(Player player) {
			switch(this.accountType)
			{
			case Player:
				return player.getUUID().equals(this.playerID) || CommandLCAdmin.isAdminPlayer(player);
			case Team:
				Team team = TeamSaveData.GetTeam(this.isClient, this.teamID);
				if(team != null && team.hasBankAccount())
					return team.canAccessBankAccount(player);
			default:
				return false;
			}
		}
		
	}
	
	public interface IMarkDirty { public void markDirty(); }
	
	public interface IBankAccountMenu
	{
		public Player getPlayer();
		public Container getCoinInput();
		public default void onDepositOrWithdraw() {}
		public boolean isClient();
		public default AccountReference getBankAccountReference() {
			return this.isClient() ? ClientBankData.GetLastSelectedAccount() : BankSaveData.GetSelectedBankAccount(this.getPlayer());
		}
		public default BankAccount getBankAccount() {
			AccountReference reference = this.getBankAccountReference();
			return reference == null ? null : reference.get();
		}
	}
	
	public interface IBankAccountAdvancedMenu extends IBankAccountMenu
	{
		public void setTransferMessage(MutableComponent component);
		public default void setNotificationLevel(CoinValue amount) {
			BankAccount account = this.getBankAccount();
			if(account != null)
				account.setNotificationValue(amount);
		}
	}
	
}
