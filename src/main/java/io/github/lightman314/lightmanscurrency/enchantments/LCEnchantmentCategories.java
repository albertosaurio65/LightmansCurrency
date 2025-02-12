package io.github.lightman314.lightmanscurrency.enchantments;

import io.github.lightman314.lightmanscurrency.items.WalletItem;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class LCEnchantmentCategories {
	
	public static final EnchantmentCategory WALLET_CATEGORY = EnchantmentCategory.create("WALLET",
			item -> item instanceof WalletItem);
	
	public static final EnchantmentCategory WALLET_PICKUP_CATEGORY = EnchantmentCategory.create("WALLET_PICKUP",
			item -> item instanceof WalletItem && WalletItem.CanPickup((WalletItem)item)
		);
	
}
