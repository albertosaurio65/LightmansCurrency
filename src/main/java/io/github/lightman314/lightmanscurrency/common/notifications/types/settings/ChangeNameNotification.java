package io.github.lightman314.lightmanscurrency.common.notifications.types.settings;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.common.notifications.Notification;
import io.github.lightman314.lightmanscurrency.common.notifications.NotificationCategory;
import io.github.lightman314.lightmanscurrency.common.notifications.categories.NullCategory;
import io.github.lightman314.lightmanscurrency.common.player.PlayerReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public class ChangeNameNotification extends Notification {

	public static final ResourceLocation TYPE = new ResourceLocation(LightmansCurrency.MODID, "changed_name");

	private PlayerReference player;
	private String oldName;
	private String newName;
	
	public ChangeNameNotification(PlayerReference player, String newName, String oldName) { this.player = player; this.newName = newName; this.oldName = oldName; }
	public ChangeNameNotification(CompoundTag compound) { this.load(compound); }
	
	@Override
	protected ResourceLocation getType() { return TYPE; }

	@Override
	public NotificationCategory getCategory() { return NullCategory.INSTANCE; }

	@Override
	public MutableComponent getMessage() {
		if(oldName.isBlank())
			return Component.translatable("log.settings.changename.set", this.player.getName(true), this.newName);
		else if(newName.isBlank())
			return Component.translatable("log.settings.changename.reset", this.player.getName(true), this.oldName);
		else
			return Component.translatable("log.settings.changename", this.player.getName(true), this.oldName, this.newName);
	}

	@Override
	protected void saveAdditional(CompoundTag compound) {
		compound.put("Player", this.player.save());
		compound.putString("OldName", this.oldName);
		compound.putString("NewName", this.newName);
	}

	@Override
	protected void loadAdditional(CompoundTag compound) {
		this.player = PlayerReference.load(compound.getCompound("Player"));
		this.oldName = compound.getString("OldName");
		this.newName = compound.getString("NewName");
	}

	@Override
	protected boolean canMerge(Notification other) {
		if(other instanceof ChangeNameNotification)
		{
			ChangeNameNotification n = (ChangeNameNotification)other;
			return n.player.is(this.player) && n.newName.equals(this.newName) && n.oldName.equals(this.oldName);
		}
		return false;
	}
	
}
