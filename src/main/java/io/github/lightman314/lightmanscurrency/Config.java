package io.github.lightman314.lightmanscurrency;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;

import io.github.lightman314.lightmanscurrency.core.ModItems;
import io.github.lightman314.lightmanscurrency.items.CoinItem;
import io.github.lightman314.lightmanscurrency.loot.LootManager;
import io.github.lightman314.lightmanscurrency.money.CoinValue;
import io.github.lightman314.lightmanscurrency.money.MoneyUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

public class Config {
	
	public static boolean canMint(Item item)
	{
		if(item == ModItems.COIN_COPPER.get())
			return SERVER.mintCopper.get();
		else if(item == ModItems.COIN_IRON.get())
			return SERVER.mintIron.get();
		else if(item == ModItems.COIN_GOLD.get())
			return SERVER.mintGold.get();
		else if(item == ModItems.COIN_EMERALD.get())
			return SERVER.mintEmerald.get();
		else if(item == ModItems.COIN_DIAMOND.get())
			return SERVER.mintDiamond.get();
		else if(item == ModItems.COIN_NETHERITE.get())
			return SERVER.mintNetherite.get();
		
		//If no rule is against it, allow the minting
		return true;
	}
	
	public static boolean canMelt(Item item)
	{
		if(item == ModItems.COIN_COPPER.get())
			return SERVER.meltCopper.get();
		else if(item == ModItems.COIN_IRON.get())
			return SERVER.meltIron.get();
		else if(item == ModItems.COIN_GOLD.get())
			return SERVER.meltGold.get();
		else if(item == ModItems.COIN_EMERALD.get())
			return SERVER.meltEmerald.get();
		else if(item == ModItems.COIN_DIAMOND.get())
			return SERVER.meltDiamond.get();
		else if(item == ModItems.COIN_NETHERITE.get())
			return SERVER.meltNetherite.get();
		
		//If no rule is against it, allow the minting
		return true;
	}
	
	public static String formatValueDisplay(double value)
	{
		return SERVER.valueFormat.get().replace("{value}", formatValueOnly(value));
	}
	public static String formatValueOnly(double value)
	{
		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(getMaxDecimal());
		return df.format(value);
	}
	
	private static int getMaxDecimal()
	{
		double minFraction = MoneyUtil.getData(new CoinValue(1).coinValues.get(0).coin).getDisplayValue() % 1d;
		if(minFraction > 0d)
		{
			//-2 to ignore the 0.
			return Double.toString(minFraction).length() - 2;
		}
		else
			return 0;
	}
	
	public static Item getBaseCoinItem() {
		Item coinItem = null;
		try{
			coinItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(SERVER.valueBaseCoin.get()));
		} catch(Exception e) { e.printStackTrace(); }
		if(coinItem != null && MoneyUtil.isCoin(coinItem))
			return coinItem;
		return ModItems.COIN_GOLD.get();
	}
	
	public static Item getMoneyMendingCoinItem() {
		Item coinItem = null;
		try {
			coinItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(SERVER.moneyMendingCoinCost.get()));
		} catch(Exception e) { e.printStackTrace(); }
		if(coinItem != null && MoneyUtil.isCoin(coinItem))
			return coinItem;
		return ModItems.COIN_COPPER.get();
	}
	
	private static Map<String,Item> traderOverrides = new HashMap<>();
	
	public static void reloadVillagerOverrides() {
		traderOverrides = new HashMap<>();
		List<? extends String> overrides = COMMON.traderOverrides.get();
		for(int i = 0; i < overrides.size(); ++i)
		{
			try {
				String override = overrides.get(i);
				if(!override.contains("-"))
					throw new RuntimeException("Input doesn't have a '-' splitter.");
				String[] split = override.split("-");
				if(split.length != 2)
					throw new RuntimeException("Input has more than 1 '-' splitter.");
				
				ResourceLocation villagerType;
				try {
					villagerType = new ResourceLocation(split[0]);
				} catch(Throwable t) { throw new RuntimeException("Villager type is not a valid resource location.", t); }
				ResourceLocation itemType;
				try {
					itemType = new ResourceLocation(split[1]);
				} catch(Throwable t) { throw new RuntimeException("Item is not a valid resource location.", t); }
				
				Item item = ForgeRegistries.ITEMS.getValue(itemType);
				if(item == null)
					throw new RuntimeException("Item '" + itemType + "' is not a registered item.");
				
				if(traderOverrides.containsKey(villagerType.toString()))
					throw new RuntimeException("Villager Type '" + villagerType + "' already has an override. Cannot override it twice!");
				
				traderOverrides.put(villagerType.toString(), item);
				LightmansCurrency.LogInfo("Trader Override loaded: " + villagerType + " -> " + itemType);
				
			} catch(Throwable t) { LightmansCurrency.LogError("Error parsing trader override input " + String.valueOf(i + 1) + ".", t); }
		}
	}
	
	public static Item getEmeraldReplacementItem(String trader) {
		
		if(traderOverrides.containsKey(trader))
			return traderOverrides.get(trader);
		
		return getDefaultEmeraldReplacementItem();
		
	}
	
	public static Item getDefaultEmeraldReplacementItem() {
		try {
			
			ResourceLocation itemType = new ResourceLocation(COMMON.defaultTraderCoin.get());
			Item item = ForgeRegistries.ITEMS.getValue(itemType);
			if(item == null)
				throw new RuntimeException("Item '" + itemType + "' is not a registered item.");
			
			return item;
			
		} catch(Throwable t) {
			LightmansCurrency.LogError("Error parsing default villager coin.", t); 
			return ModItems.COIN_EMERALD.get();
		}
	}
	
	public static class Client
	{
		
		public enum TraderRenderType { FULL(Integer.MAX_VALUE), PARTIAL(1), NONE(0);
			public final int renderLimit;
			TraderRenderType(int renderLimit) { this.renderLimit = renderLimit; } 
		}
		
		//Render Options
		public final ForgeConfigSpec.IntValue itemRenderLimit;

		//Timestamp Formatting Options
		public final ForgeConfigSpec.ConfigValue<String> timeFormat;
		
		//Wallet Button Options
		public final ForgeConfigSpec.IntValue walletSlotX;
		public final ForgeConfigSpec.IntValue walletSlotY;
		public final ForgeConfigSpec.IntValue walletSlotCreativeX;
		public final ForgeConfigSpec.IntValue walletSlotCreativeY;
		public final ForgeConfigSpec.IntValue walletButtonOffsetX;
		public final ForgeConfigSpec.IntValue walletButtonOffsetY;
		
		//Notification Options
		public final ForgeConfigSpec.BooleanValue pushNotificationsToChat;
		
		//Inventory Button Options
		public final ForgeConfigSpec.IntValue notificationAndTeamButtonX;
		public final ForgeConfigSpec.IntValue notificationAndTeamButtonY;
		public final ForgeConfigSpec.IntValue notificationAndTeamButtonXCreative;
		public final ForgeConfigSpec.IntValue notificationAndTeamButtonYCreative;
		
		//Sound Options
		public final ForgeConfigSpec.BooleanValue moneyMendingClink;
		
		
		Client(ForgeConfigSpec.Builder builder)
		{
			builder.comment("Client configuration settings").push("client");
			
			builder.comment("Quality Settings").push("quality");
			
			this.itemRenderLimit = builder
					.comment("Maximum number of items each Item Trader can render (per-trade) as stock. Lower to decrease client-lag in trader-rich areas.",
							"Setting to 0 will disable item rendering entirely, so use with caution.")
							.defineInRange("itemTraderRenderLimit", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
			
			builder.pop();

			builder.comment("Time Formatting Settings").push("time");

			this.timeFormat = builder
					.comment("How Notification Timestamps are displayed.","Follows SimpleDateFormat formatting: https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html")
					.define("timeFormatting","MM/dd/yy hh:mmaa");

			builder.pop();
			
			builder.comment("Wallet Slot Settings").push("wallet_slot");
			
			this.walletSlotX = builder
					.comment("The x position that the wallet slot will be placed at in the players inventory.")
					.defineInRange("slotX", 76, -255, 255);
			this.walletSlotY = builder
					.comment("The y position that the wallet slot will be placed at in the players inventory.")
					.defineInRange("slotY", 43, -255, 255);
			
			this.walletSlotCreativeX = builder
					.comment("The x position that the wallet slot will be placed at in the players creative inventory.")
					.defineInRange("creativeSlotX", 126, -255, 255);
			this.walletSlotCreativeY = builder
					.comment("The y position that the wallet slot will be placed at in the players creative inventory.")
					.defineInRange("creativeSlotY", 19, -255, 255);
			
			this.walletButtonOffsetX = builder
					.comment("The x offset that the wallet button should be placed at relative to the wallet slot position.")
					.defineInRange("buttonX", 8, -255, 255);
			this.walletButtonOffsetY = builder
					.comment("The y offset that the wallet button should be placed at relative to the wallet slot position.")
					.defineInRange("buttonY", -10, -255, 255);
			
			builder.pop();
			
			builder.comment("Inventory Button Settings").push("inventory_buttons");
			
			this.notificationAndTeamButtonX = builder
					.comment("The x position that the notification & team manager buttons will be placed at in the players inventory.")
					.defineInRange("buttonX", 152, Integer.MIN_VALUE, Integer.MAX_VALUE);
			this.notificationAndTeamButtonY = builder
					.comment("The x position that the notification & team manager buttons will be placed at in the players inventory.")
					.defineInRange("buttonY", 3, Integer.MIN_VALUE, Integer.MAX_VALUE);
			
			this.notificationAndTeamButtonXCreative = builder
					.comment("The x position that the notification & team manager buttons will be placed at in the players creative inventory.")
					.defineInRange("buttonCreativeX", 171, Integer.MIN_VALUE, Integer.MAX_VALUE);
			this.notificationAndTeamButtonYCreative = builder
					.comment("The y position that the notification & team manager buttons will be placed at in the players creative inventory.")
					.defineInRange("buttonCreativeY", 3, Integer.MIN_VALUE, Integer.MAX_VALUE);
			
			builder.pop();
			
			builder.comment("Notification Settings").push("notification");
			
			this.pushNotificationsToChat = builder
					.comment("Whether notifications should be posted in your chat when you receive them.")
					.define("notificationsInChat", true);
			
			builder.pop();
			
			builder.comment("Sound Settings").push("sounds");
			
			this.moneyMendingClink = builder
					.comment("Whether Money Mending should make a noise when triggered.")
					.define("moneyMendingClink", true);
			
			builder.pop();
			
		}
		
	}
	
	public static class Common
	{
		
		//Crafting Options
		public final ForgeConfigSpec.BooleanValue canCraftNetworkTraders;
		public final ForgeConfigSpec.BooleanValue canCraftTraderInterfaces;
		
		//Custom trades
		public final ForgeConfigSpec.BooleanValue addCustomWanderingTrades;
		public final ForgeConfigSpec.BooleanValue addBankerVillager;
		public final ForgeConfigSpec.BooleanValue addCashierVillager;
		public final ForgeConfigSpec.BooleanValue changeVanillaTrades;
		public final ForgeConfigSpec.BooleanValue changeModdedTrades;
		public final ForgeConfigSpec.BooleanValue changeWanderingTrades;
		public final ForgeConfigSpec.ConfigValue<String> defaultTraderCoin;
		public final ForgeConfigSpec.ConfigValue<List<? extends String>> traderOverrides;
		
		//Debug
		public final ForgeConfigSpec.IntValue debugLevel;
		
		//Entity Loot
		public final ForgeConfigSpec.BooleanValue enableEntityDrops;
		public final ForgeConfigSpec.BooleanValue enableSpawnerEntityDrops;
		public final ForgeConfigSpec.BooleanValue allowFakePlayerCoinDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> copperEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> ironEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> goldEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> emeraldEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> diamondEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> netheriteEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> bossCopperEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> bossIronEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> bossGoldEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> bossEmeraldEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> bossDiamondEntityDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> bossNetheriteEntityDrops;
		
		//Chest Loot
		public final ForgeConfigSpec.BooleanValue enableChestLoot;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> copperChestDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> ironChestDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> goldChestDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> emeraldChestDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> diamondChestDrops;
		public final ForgeConfigSpec.ConfigValue<List <? extends String>> netheriteChestDrops;
		
		Common(ForgeConfigSpec.Builder builder)
		{
			
			builder.comment("Common configuration settings").push("common");
			
			builder.comment("Crafting Settings.").push("crafting");
			
			this.canCraftNetworkTraders = builder.comment("Whether Network Traders can be crafted.",
					"Disabling will not remove any existing Network Traders from the world, nor prevent their use.",
					"Disabling does NOT disable the recipes of Network Upgrades or the Trading Terminals.",
					"/reload required for changes to take effect.")
					.define("allowNetworkTraderCrafting", true);
			
			this.canCraftTraderInterfaces = builder.comment("Whether Trader Interface blocks can be crafted.",
					"Disabling will not remove any existing Trader Interfaces from the world, nor prevent their use.",
					"/reload required for changes to take effect.")
					.define("allowTraderInterfaceCrafting", true);
			
			builder.pop();
			
			builder.comment("Villager Related Settings.","Note: Any changes to villagers requires a full reboot to be applied due to how Minecraft/Forge registers trades.").push("villagers");
			
			this.addCustomWanderingTrades = builder
					.comment("Whether the wandering trader will have additional trades that allow you to buy misc items with money.")
					.define("addCustomWanderingTrades", true);
			
			this.addBankerVillager = builder
					.comment("Whether the banker villager profession will have any registered trades. The banker sells Lightman's Currency items for coins.")
					.define("addBanker", true);
			this.addCashierVillager = builder
					.comment("Whether the cashier villager profession will have any registered trades.. The cashier sells an amalgamation of vanilla traders products for coins.")
					.define("addCashier", true);
			
			builder.comment("Settings Related to other Villagers").push("other_traders");
			
			this.changeVanillaTrades = builder
					.comment("Whether vanilla villagers should have the Emeralds from their trades replaced with coins.")
					.define("changeVanillaTrades", false);
			
			this.changeModdedTrades = builder
					.comment("Whether villagers added by other mods should have the Emeralds from their trades replaced with coins.")
					.define("changeModdedTrades", false);
			
			this.changeWanderingTrades = builder
					.comment("Whether the wandering trader should have the emeralds from their trades replaced with the default trader coin.")
					.define("changeWanderingTrades", false);
			
			this.defaultTraderCoin = builder
					.comment("The default coin to replace a traders emeralds with.")
					.define("defaultTraderCoin", "lightmanscurrency:coin_emerald");
			
			this.traderOverrides = builder
					.comment("List of trader coin overrides.",
							"Each entry must be formatted as follows: \"mod:some_trader_type-lightmanscurrency:some_coin\"",
							"Every trader not on this list will use the default trader coin defined above.")
					.define("traderOverrides", Lists.newArrayList(
							"minecraft:butcher-lightmanscurrency:coin_iron",
							"minecraft:cartographer-lightmanscurrency:coin_iron",
							"minecraft:farmer-lightmanscurrency:coin_iron",
							"minecraft:fisherman-lightmanscurrency:coin_iron",
							"minecraft:fletcher-lightmanscurrency:coin_copper",
							"minecraft:leatherworker-lightmanscurrency:coin_iron",
							"minecraft:mason-lightmanscurrency:coin_iron",
							"minecraft:shepherd-lightmanscurrency:coin_iron"
							));
			
			builder.pop();
			
			builder.pop();
			
			this.debugLevel = builder
					.comment("Level of debug messages to be shown in the logs.","0-All debug messages. 1-Warnings/Errors only. 2-Errors only. 3-No debug messages.","Note: All debug messages will still be sent debug.log regardless of settings.")
					.defineInRange("debugLevel", 0, 0, 3);
			
			//Entity loot modification
			builder.comment("Entity loot settings. Accepts entity ids (i.e. minecraft:zombie)").push("entity_loot");
			
			this.enableEntityDrops = builder
					.comment("Whether coins can be dropped by entities. Does not effect chest loot generation.")
					.define("enableEntityDrops", true);
			//Entity spawned loot drops
			this.enableSpawnerEntityDrops = builder
					.comment("Whether coins can be dropped by entities that were spawned by the vanilla spawner.")
					.define("enableSpawnerEntityDrops", false);
			//Fake Player loot drops
			this.allowFakePlayerCoinDrops = builder
					.comment("Whether modded machines that emulate player behaviour can trigger coin drops from entities.",
							"Set to false to help prevent coin farming.")
					.define("allowFakePlayerTrigger", true);
			
			//Copper
			this.copperEntityDrops = builder
					.comment("Entities that will occasionally drop copper coins.")
					.defineList("copper", LootManager.ENTITY_COPPER_DROPLIST, o -> o instanceof String);
			//Iron
			this.ironEntityDrops = builder
					.comment("Entities that will occasionally drop copper -> iron coins.")
					.defineList("iron", LootManager.ENTITY_IRON_DROPLIST, o -> o instanceof String);
			//Gold
			this.goldEntityDrops = builder
					.comment("Entities that will occasionally drop copper -> gold coins.")
					.defineList("gold", LootManager.ENTITY_GOLD_DROPLIST, o -> o instanceof String);
			//Emerald
			this.emeraldEntityDrops = builder
					.comment("Entities that will occasionally drop copper -> emerald coins.")
					.defineList("emerald", LootManager.ENTITY_EMERALD_DROPLIST, o -> o instanceof String);
			//Diamond
			this.diamondEntityDrops = builder
					.comment("Entities that will occasionally drop copper -> diamond coins.")
					.defineList("diamond", LootManager.ENTITY_DIAMOND_DROPLIST, o -> o instanceof String);
			//Netherite
			this.netheriteEntityDrops = builder
					.comment("Entities that will occasionally drop copper -> netherite coins.")
					.defineList("netherite", LootManager.ENTITY_NETHERITE_DROPLIST, o -> o instanceof String);
			
			//Boss
			this.bossCopperEntityDrops = builder
					.comment("Entities that will drop a large amount of copper coins.")
					.defineList("boss_copper", LootManager.ENTITY_BOSS_COPPER_DROPLIST, o -> o instanceof String);
			this.bossIronEntityDrops = builder
					.comment("Entities that will drop a large amount of copper -> iron coins.")
					.defineList("boss_iron", LootManager.ENTITY_BOSS_IRON_DROPLIST, o -> o instanceof String);
			this.bossGoldEntityDrops = builder
					.comment("Entities that will drop a large amount of copper -> gold coins.")
					.defineList("boss_gold", LootManager.ENTITY_BOSS_GOLD_DROPLIST, o -> o instanceof String);
			this.bossEmeraldEntityDrops = builder
					.comment("Entities that will drop a large amount of copper -> emerald coins.")
					.defineList("boss_emerald", LootManager.ENTITY_BOSS_EMERALD_DROPLIST, o -> o instanceof String);
			this.bossDiamondEntityDrops = builder
					.comment("Entities that will drop a large amount of copper -> diamond coins.")
					.defineList("boss_diamond", LootManager.ENTITY_BOSS_DIAMOND_DROPLIST, o -> o instanceof String);
			this.bossNetheriteEntityDrops = builder
					.comment("Entities that will drop a large amount of copper -> netherite coins.")
					.defineList("boss_netherite", LootManager.ENTITY_BOSS_NETHERITE_DROPLIST, o -> o instanceof String);
			
			builder.pop();
			
			builder.comment("Chest loot settings.").push("chest_loot");
			this.enableChestLoot = builder
					.comment("Whether coins can spawn in chests Does not effect entity loot drops.")
					.define("enableChestLoot", true);
			this.copperChestDrops = builder
					.comment("Chests that will occasionally spawn copper coins.")
					.defineList("copper", LootManager.CHEST_COPPER_DROPLIST, o -> o instanceof String);
			this.ironChestDrops = builder
					.comment("Chests that will occasionally spawn copper -> iron coins.")
					.defineList("iron", LootManager.CHEST_IRON_DROPLIST, o -> o instanceof String);
			this.goldChestDrops = builder
					.comment("Chests that will occasionally spawn copper -> gold coins.")
					.defineList("gold", LootManager.CHEST_GOLD_DROPLIST, o -> o instanceof String);
			this.emeraldChestDrops = builder
					.comment("Chests that will occasionally spawn copper -> emerald coins.")
					.defineList("emerald", LootManager.CHEST_EMERALD_DROPLIST, o -> o instanceof String);
			this.diamondChestDrops = builder
					.comment("Chests that will occasionally spawn copper -> diamond coins.")
					.defineList("diamond", LootManager.CHEST_DIAMOND_DROPLIST, o -> o instanceof String);
			this.netheriteChestDrops = builder
					.comment("Chests that will occasionally spawn copper -> netherite coins.")
					.defineList("netherite", LootManager.CHEST_NETHERITE_DROPLIST, o -> o instanceof String);
			
			builder.pop();
			
			
		}
		
	}
	
	public static class Server
	{
		
		//Log Limit Option
		public final ForgeConfigSpec.IntValue logLimit;
		public final ForgeConfigSpec.IntValue notificationLimit;
		
		//Ejection Options
		public final ForgeConfigSpec.BooleanValue safelyEjectIllegalBreaks;
		
		//Melt/Mint Options
		public final ForgeConfigSpec.BooleanValue allowCoinMinting;
		public final ForgeConfigSpec.BooleanValue allowCoinMelting;
		
		//Specific Melt/Mint Options
		public final ForgeConfigSpec.BooleanValue mintCopper;
		public final ForgeConfigSpec.BooleanValue mintIron;
		public final ForgeConfigSpec.BooleanValue mintGold;
		public final ForgeConfigSpec.BooleanValue mintEmerald;
		public final ForgeConfigSpec.BooleanValue mintDiamond;
		public final ForgeConfigSpec.BooleanValue mintNetherite;
		
		public final ForgeConfigSpec.BooleanValue meltCopper;
		public final ForgeConfigSpec.BooleanValue meltIron;
		public final ForgeConfigSpec.BooleanValue meltGold;
		public final ForgeConfigSpec.BooleanValue meltEmerald;
		public final ForgeConfigSpec.BooleanValue meltDiamond;
		public final ForgeConfigSpec.BooleanValue meltNetherite;
		
		//Wallet Options
		public final ForgeConfigSpec.IntValue walletConvertLevel;
		public final ForgeConfigSpec.IntValue walletPickupLevel;
		public final ForgeConfigSpec.IntValue walletBankLevel;
		
		//Value Display Options
		public final ForgeConfigSpec.EnumValue<CoinItem.CoinItemTooltipType> coinTooltipType;
		public final ForgeConfigSpec.EnumValue<CoinValue.ValueType> coinValueType;
		public final ForgeConfigSpec.EnumValue<CoinValue.ValueType> coinValueInputType;
		public final ForgeConfigSpec.ConfigValue<String> valueBaseCoin;
		public final ForgeConfigSpec.ConfigValue<String> valueFormat;
		
		//Capacity Upgrade Options
		public final ForgeConfigSpec.IntValue itemUpgradeCapacity1;
		public final ForgeConfigSpec.IntValue itemUpgradeCapacity2;
		public final ForgeConfigSpec.IntValue itemUpgradeCapacity3;
		
		//Enchantment Options
		public final ForgeConfigSpec.ConfigValue<String> moneyMendingCoinCost;
		public final ForgeConfigSpec.IntValue coinMagnetRangeBase;
		public final ForgeConfigSpec.IntValue coinMagnetRangeLevel;
		
		//Auction House Options
		public final ForgeConfigSpec.BooleanValue enableAuctionHouse;
		public final ForgeConfigSpec.IntValue maxAuctionDuration;
		public final ForgeConfigSpec.IntValue minAuctionDuration;
		
		//Discord Bot Options
		public final ForgeConfigSpec.ConfigValue<String> currencyChannel;
		public final ForgeConfigSpec.ConfigValue<String> currencyCommandPrefix;
		public final ForgeConfigSpec.BooleanValue limitSearchToNetworkTraders;
		
		//Discord Bot Notification Options
		public final ForgeConfigSpec.BooleanValue traderCreationNotifications;
		public final ForgeConfigSpec.BooleanValue auctionHouseCreateNotifications;
		public final ForgeConfigSpec.BooleanValue auctionHouseCreatePersistentNotifications;
		public final ForgeConfigSpec.BooleanValue auctionHouseCancelNotifications;
		public final ForgeConfigSpec.BooleanValue auctionHouseWinNotifications;
		
		
		Server(ForgeConfigSpec.Builder builder)
		{
			
			builder.comment("Server Config Settings").push("server");
			
			this.logLimit = builder
					.comment("The maximum number of text log entries allowed before old entries are deleted.",
							"Lower if you encounter packet size problems.")
					.defineInRange("logLimit", 100, 0, Integer.MAX_VALUE);
			
			this.notificationLimit = builder
					.comment("The maximum number of notifications each player can have before old entries are deleted.",
							"Lower if you encounter packet size problems.")
					.defineInRange("notificationLimit", 500, 0, Integer.MAX_VALUE);
			
			this.safelyEjectIllegalBreaks = builder
					.comment("Whether illegally broken traders (such as being replaced with /setblock, or modded machines that break blocks) will safely eject their block/contents into a temporary storage area for the owner to collect safely.",
							"If disabled, illegally broken traders will throw their items on the ground, and can thus be griefed by modded machines.")
					.define("ejectIllegalBreaks", true);
			
			this.allowCoinMinting = builder
					.comment("Determines whether or not coins should be craftable via the Coin Minting Machine.")
					.translation("lightmanscurrency.configgui.canMintCoins")
					.define("canMintCoins", true);
			this.allowCoinMelting = builder
					.comment("Determines whether or not coins can be melted back into their source material in the Coin Minting Machine.")
					.translation("lightmanscurrency.configgui.canMeltCoins")
					.define("canMeltCoins", false);
			
			builder.comment("Specific Coin Minting Settings.").push("coin_minting");
			this.mintCopper = builder.comment("Whether copper coins can be minted.")
					.define("canMintCopper", true);
			this.mintIron = builder.comment("Whether iron coins can be minted.")
					.define("canMintIron", true);
			this.mintGold = builder.comment("Whether gold coins can be minted.")
					.define("canMintGold", true);
			this.mintEmerald = builder.comment("Whether emerald coins can be minted.")
					.define("canMintEmerald", true);
			this.mintDiamond = builder.comment("Whether diamond coins can be minted.")
					.define("canMintDiamond", true);
			this.mintNetherite = builder.comment("Whether netherite coins can be minted.")
					.define("canMintNetherite", true);
			builder.pop();
			
			builder.comment("Specific Coin Melting Settings.").push("coin_melting");
			this.meltCopper = builder.comment("Whether copper coins can be melted.")
					.define("canMeltCopper", true);
			this.meltIron = builder.comment("Whether iron coins can be melted.")
					.define("canMeltIron", true);
			this.meltGold = builder.comment("Whether gold coins can be melted.")
					.define("canMeltGold", true);
			this.meltEmerald = builder.comment("Whether emerald coins can be melted.")
					.define("canMeltEmerald", true);
			this.meltDiamond = builder.comment("Whether diamond coins can be melted.")
					.define("canMeltDiamond", true);
			this.meltNetherite = builder.comment("Whether netherite coins can be melted.")
					.define("canMeltNetherite", true);
			builder.pop();
			
			builder.comment("Wallet Settings.").push("wallet");
			
			this.walletConvertLevel = builder.comment("The lowest level wallet capable of converting coins in the UI.",
						"0-Copper Wallet; 1-Iron Wallet; 2-Gold Wallet; 3-Emerald Wallet; 4-Diamond Wallet; 5-Netherite Wallet",
						"Must be less than or equal to 'pickupLevel'.")
					.defineInRange("convertLevel", 1, 0, 5);
			
			this.walletPickupLevel = builder.comment("The lowest level wallet capable of automatically collecting coins while equipped.",
						"0-Copper Wallet; 1-Iron Wallet; 2-Gold Wallet; 3-Emerald Wallet; 4-Diamond Wallet; 5-Netherite Wallet")
					.defineInRange("pickupLevel", 2, 0, 5);
			
			this.walletBankLevel = builder.comment("The lowest level wallet capable of allowing transfers to/from your bank account.",
						"0-Copper Wallet; 1-Iron Wallet; 2-Gold Wallet; 3-Emerald Wallet; 4-Diamond Wallet; 5-Netherite Wallet")
					.defineInRange("bankLevel", 5, 0, 5);
			
			builder.pop();
			
			builder.comment("Coin value display settings.").push("coin_value_display");
			
			this.coinTooltipType = builder
					.comment("Tooltip type displayed on coin items.",
							"DEFAULT: Conversion tooltips, explaining it's value based on the coins it can be converted to/from.",
							"VALUE: Coins numerical display value as defined by the coinValueType option below. Not recommend if using the DEFAULT coinValueType.")
					.defineEnum("coinTooltipType", CoinItem.CoinItemTooltipType.DEFAULT);
			
			this.coinValueType = builder
					.comment("Value display method used throughout the mod.",
							"DEFAULT: Coin Count & Icon aglomerate (1n5g for 1 netherite and 5 gold)",
							"VALUE: Coin numerical display value as defined by the baseValueCoin and valueFormat config options below.")
					.defineEnum("coinValueType", CoinValue.ValueType.DEFAULT);
			
			this.coinValueInputType = builder
					.comment("Input method used for the Coin Value Input.",
							"DEFAULT: Default coin input with up/down buttons for each coin type.",
							"VALUE: Text box input for the coins display value.")
					.defineEnum("coinValueInputType", CoinValue.ValueType.DEFAULT);
			
			this.valueBaseCoin = builder
					.comment("Coin item defined as 1 value unit for display purposes. Any coins worth less than the base coin will have a decimal value.")
					.define("baseValueCoin", "lightmanscurrency:coin_copper");
			
			this.valueFormat = builder
					.comment("Value display format. Used to add currency signs to coin value displays.",
							"{value} will be replaced with the coins numerical value. Only 1 should be present at any given time.")
					.define("valueFormat", "${value}");
					
			
			builder.pop();
			
			builder.comment("Item Capacity Upgrade Settings").push("upgrades");
			
			this.itemUpgradeCapacity1 = builder.comment("The amount of item storage added by the first Item Capacity upgrade (Iron).")
					.defineInRange("upgradeCapacity1", 3 * 64, 1, 1728);
			this.itemUpgradeCapacity2 = builder.comment("The amount of item storage added by the second Item Capacity upgrade (Gold).")
					.defineInRange("upgradeCapacity2", 6 * 64, 1, 1728);
			this.itemUpgradeCapacity3 = builder.comment("The amount of item storage added by the third Item Capacity upgrade (Diamond).")
					.defineInRange("upgradeCapacity3", 9 * 64, 1, 1728);
			
			builder.pop();
			
			builder.comment("Enchantment Settings").push("enchantments");
			
			this.moneyMendingCoinCost = builder.comment("The coin cost required to repair a single item durability point with the Money Mending enchantment.")
					.define("moneyMendingCoinCost", "lightmanscurrency:coin_copper");
			
			this.coinMagnetRangeBase = builder.comment("The base radius around the player that the Coin Magnet enchantment will collect coins from.")
					.defineInRange("coinMagnetRangeBase", 5, 0, 50);
			this.coinMagnetRangeLevel = builder.comment("The increase in collection radius added by each additional level of the enchantment.")
					.defineInRange("coinMagnetRangeLevel", 2, 0, 50);
			
			
			builder.pop();
			
			builder.comment("Auction House Settings").push("auction_house");
			
			this.enableAuctionHouse = builder.comment("Whether the Auction House will appear on the trader list.",
					"If disabled after players have interacted with it, items & money in the auction house cannot be accessed until re-enabled.")
					.define("enabled", true);
			
			this.minAuctionDuration = builder.comment("The minimum number of days an auction can be carried out.")
					.defineInRange("minDuration", 0, 0, Integer.MAX_VALUE);
			
			this.maxAuctionDuration = builder.comment("The maximum number of days an auction can be carried out.")
					.defineInRange("maxDuration", 30, 1, Integer.MAX_VALUE);
			
			builder.pop();
			
			builder.comment("Discord bot settings. Requires lightmansdiscord v0.0.3.0+ to use.").push("discord");
				
			this.currencyChannel = builder
					.comment("The channel where users can run the currency commands and where currency related announcements will be made.")
					.define("channel", "000000000000000000");
			this.currencyCommandPrefix = builder
					.comment("Prefix for currency commands.")
					.define("prefix", "!");
			this.limitSearchToNetworkTraders = builder
					.comment("Whether the !search command should limit its search results to only Network Traders, or if it should list all traders.")
					.define("limitSearchToNetwork", true);
			
			builder.comment("Bot notification options.").push("notifications");
			
			this.traderCreationNotifications = builder
					.comment("Whether a notification will appear in the currency bot channel when a Network Trader is created.",
							"Notification will have a 60 second delay to allow them time to customize the traders name, etc.")
					.define("networkTraderBuilt", true);
			
			this.auctionHouseCreateNotifications = builder
					.comment("Whether a notification will appear in the currency bot channel when an Auction is created in the Auction House.")
					.define("auctionHouseCreated", true);
			
			this.auctionHouseCreatePersistentNotifications = builder
					.comment("Whether a notification will appear in the currency bot channel when a Persistent Auction is created automatically.",
							"Requires that auction house creation notifications also be enabled.")
					.define("auctionHousePersistentCreations", true);
			
			this.auctionHouseCancelNotifications = builder
					.comment("Whether a notification will appear in the currency bot channel when an Auction is cancelled in the Auction House.")
					.define("auctionHouseCancelled", false);
			
			this.auctionHouseWinNotifications = builder
					.comment("Whether a notification will appear in the currency bot channel when an Auction is completed and had a bidder.")
					.define("auctionHouseWon", true);
			
			builder.pop();
			
			builder.pop();
			
		}
		
	}
	
	public static final ForgeConfigSpec clientSpec;
	public static final Config.Client CLIENT;
	public static final ForgeConfigSpec commonSpec;
	public static final Config.Common COMMON;
	public static final ForgeConfigSpec serverSpec;
	public static final Config.Server SERVER;
	
	static
	{
		//Client
		final Pair<Client,ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(Config.Client::new);
		clientSpec = clientPair.getRight();
		CLIENT = clientPair.getLeft();
		//Common
		final Pair<Common,ForgeConfigSpec> commonPair = new ForgeConfigSpec.Builder().configure(Config.Common::new);
		commonSpec = commonPair.getRight();
		COMMON = commonPair.getLeft();
		//Server
		final Pair<Server,ForgeConfigSpec> serverPair = new ForgeConfigSpec.Builder().configure(Config.Server::new);
		serverSpec = serverPair.getRight();
		SERVER = serverPair.getLeft();
	}
	
}
