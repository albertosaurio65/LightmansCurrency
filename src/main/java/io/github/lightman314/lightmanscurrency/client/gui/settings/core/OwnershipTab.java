package io.github.lightman314.lightmanscurrency.client.gui.settings.core;

import java.util.List;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.lightman314.lightmanscurrency.client.gui.screen.TraderSettingsScreen;
import io.github.lightman314.lightmanscurrency.client.gui.settings.SettingsTab;
import io.github.lightman314.lightmanscurrency.client.gui.widget.TeamSelectWidget;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.icon.IconData;
import io.github.lightman314.lightmanscurrency.client.util.ItemRenderUtil;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import io.github.lightman314.lightmanscurrency.common.teams.TeamSaveData;
import io.github.lightman314.lightmanscurrency.common.traders.permissions.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class OwnershipTab extends SettingsTab{

	public static final OwnershipTab INSTANCE = new OwnershipTab();
	
	@Override
	public int getColor() {
		return 0xFFFFFF;
	}

	@Override
	public IconData getIcon() { return IconData.of(ItemRenderUtil.getAlexHead()); }
	
	@Override
	public MutableComponent getTooltip() { return Component.translatable("tooltip.lightmanscurrency.settings.owner"); }
	
	private OwnershipTab() { }
	
	EditBox newOwnerInput;
	Button buttonSetOwner;
	TeamSelectWidget teamSelection;
	Button buttonSetTeamOwner;
	
	long selectedTeam = -1;
	List<Team> teamList = Lists.newArrayList();
	
	@Override
	public boolean canOpen() { return this.hasPermissions(Permissions.TRANSFER_OWNERSHIP); }

	@Override
	public void initTab() {
		
		TraderSettingsScreen screen = this.getScreen();
		
		this.newOwnerInput = screen.addRenderableTabWidget(new EditBox(screen.getFont(), screen.guiLeft() + 20, screen.guiTop() + 20, 160, 20, Component.empty()));
		this.newOwnerInput.setMaxLength(16);
		
		this.buttonSetOwner = screen.addRenderableTabWidget(new Button(screen.guiLeft() + 20, screen.guiTop() + 41, 160, 20, Component.translatable("gui.button.lightmanscurrency.set_owner"), this::setOwner));
		this.buttonSetOwner.active = false;
		
		this.teamSelection = screen.addRenderableTabWidget(new TeamSelectWidget(screen.guiLeft() + 10, screen.guiTop() + 65, 5, () -> this.teamList, this::getSelectedTeam, this::selectTeam));
		this.teamSelection.init(screen::addRenderableTabWidget, this.getFont());
		
		this.buttonSetTeamOwner = screen.addRenderableTabWidget(new Button(screen.guiLeft() + 20, screen.guiTop() + 170, 160, 20, Component.translatable("gui.button.lightmanscurrency.set_owner"), this::setTeamOwner));
		this.buttonSetTeamOwner.active = false;
		
	}
	
	private Team getTeam(int teamIndex)
	{
		if(teamIndex < this.teamList.size())
			return this.teamList.get(teamIndex);
		return null;
	}
	
	private Team getSelectedTeam()
	{
		if(this.selectedTeam < 0)
			return null;
		return TeamSaveData.GetTeam(true, this.selectedTeam);
	}
	
	private void refreshTeamList()
	{
		this.teamList = Lists.newArrayList();
		List<Team> allTeams = TeamSaveData.GetAllTeams(true);
		allTeams.forEach(team ->{
			if(team.isMember(this.getPlayer()))
				this.teamList.add(team);
		});
		this.teamList.sort(Team.sorterFor(this.getPlayer()));
	}

	@Override
	public void preRender(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
		
		TraderSettingsScreen screen = this.getScreen();
		
		this.getFont().draw(pose, Component.translatable("gui.button.lightmanscurrency.team.owner", this.getScreen().getTrader().getOwner().getOwnerName(true)), screen.guiLeft() + 20, screen.guiTop() + 10, 0x404040);
		
	}
	
	@Override
	public void postRender(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
		TraderSettingsScreen screen = this.getScreen();
		//Render button tooltips
		if(this.buttonSetOwner.isMouseOver(mouseX, mouseY) || this.buttonSetTeamOwner.isMouseOver(mouseX, mouseY))
		{
			screen.renderTooltip(pose, Component.translatable("tooltip.lightmanscurrency.warning").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW), mouseX, mouseY);
		}
	}

	@Override
	public void tick() {
		
		this.refreshTeamList();
		
		this.newOwnerInput.tick();
		
		this.buttonSetOwner.active = !this.newOwnerInput.getValue().isBlank();
		this.buttonSetTeamOwner.active = this.getSelectedTeam() != null;
		
	}

	@Override
	public void closeTab() {
		//Reset the selected team & team list to save space
		this.selectedTeam = -1;
		this.teamList = Lists.newArrayList();
	}

	private void selectTeam(int teamIndex)
	{
		Team newTeam = this.getTeam(teamIndex);
		if(newTeam != null)
		{
			if(newTeam.getID() == this.selectedTeam)
				this.selectedTeam = -1;
			else
				this.selectedTeam = newTeam.getID();
		}
	}

	private void setOwner(Button button)
	{
		if(this.newOwnerInput.getValue().isBlank())
			return;
		CompoundTag message = new CompoundTag();
		message.putString("ChangePlayerOwner", this.newOwnerInput.getValue());
		this.getScreen().getTrader().sendNetworkMessage(message);
		this.newOwnerInput.setValue("");
	}
	
	private void setTeamOwner(Button button)
	{
		if(this.getSelectedTeam() == null)
			return;
		CompoundTag message = new CompoundTag();
		message.putLong("ChangeTeamOwner", this.selectedTeam);
		this.getScreen().getTrader().sendNetworkMessage(message);
		this.selectedTeam = -1;
	}
	
}
