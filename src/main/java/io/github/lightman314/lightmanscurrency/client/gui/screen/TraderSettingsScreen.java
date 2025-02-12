package io.github.lightman314.lightmanscurrency.client.gui.screen;

import java.util.List;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.client.gui.settings.SettingsTab;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.IconButton;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.TabButton;
import io.github.lightman314.lightmanscurrency.client.util.IconAndButtonUtil;
import io.github.lightman314.lightmanscurrency.common.traders.TraderData;
import io.github.lightman314.lightmanscurrency.common.traders.permissions.Permissions;
import io.github.lightman314.lightmanscurrency.network.LightmansCurrencyPacketHandler;
import io.github.lightman314.lightmanscurrency.network.message.trader.MessageOpenStorage;
import io.github.lightman314.lightmanscurrency.network.message.trader.MessageOpenTrades;
import io.github.lightman314.lightmanscurrency.util.MathUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class TraderSettingsScreen extends Screen{

	public static final ResourceLocation GUI_TEXTURE =  new ResourceLocation(LightmansCurrency.MODID, "textures/gui/tradersettings.png");
	
	public Player getPlayer() { return this.minecraft.player; }
	private final Supplier<TraderData> trader;
	public TraderData getTrader() { return this.trader.get(); }
	
	public Font getFont() { return this.font; }
	
	public final int guiLeft() { return (this.width - this.xSize) / 2; }
	public final int guiTop() { return (this.height - this.ySize) / 2; }
	public final int xSize = 200;
	public final int ySize = 200;
	
	List<AbstractWidget> tabWidgets = Lists.newArrayList();
	List<GuiEventListener> tabListeners = Lists.newArrayList();
	
	List<TabButton> tabButtons = Lists.newArrayList();
	
	List<SettingsTab> tabs;
	int currentTabIndex = 0;
	public SettingsTab currentTab()
	{
		return this.tabs.get(MathUtil.clamp(currentTabIndex, 0, this.tabs.size() - 1));
	}
	
	public TraderSettingsScreen(Supplier<TraderData> trader)
	{
		super(Component.empty());
		
		this.trader = trader;
		
		//Collect the Settings Tabs
		this.tabs = this.trader.get().getSettingsTabs();
		
		this.tabs.forEach(tab -> tab.setScreen(this));
		
	}
	
	@Override
	public void init()
	{
		//Initialize the back button
		this.addRenderableWidget(new IconButton(this.guiLeft(), this.guiTop() - 20, this::OpenStorage, IconAndButtonUtil.ICON_BACK));
		//Initialize the tab buttons
		for(int i = 0; i < this.tabs.size(); ++i)
		{
			TabButton button = this.addRenderableWidget(new TabButton(this::clickedOnTab, this.font, this.tabs.get(i)));
			button.active = i != this.currentTabIndex;
			button.visible = this.tabs.get(i).canOpen();
			this.tabButtons.add(button);
		}
		this.positionTabButtons();
		
		//Initialize the starting tab
		try {
			this.currentTab().initTab();
		} catch(Throwable t) { LightmansCurrency.LogError("Error in Settings Tab init.", t); }
		
		
	}
	
	private int getTabPosX(int index)
	{
		if(index < 7)
			return this.guiLeft() + 20 + 25 * index;
		if(index < 15)
			return this.guiLeft() + this.xSize;
		if(index < 23)
			return this.guiLeft() + this.xSize - 25 * (index - 15);
		return this.guiLeft() - 25;
	}
	
	private int getTabPosY(int index)
	{
		if(index < 7)
			return this.guiTop() - 25;
		if(index < 15)
			return this.guiTop() + 25 * (index - 10);
		if(index < 23)
			return this.guiTop() + this.ySize;
		return this.guiTop() + this.ySize - 25 * (index - 23);
	}
	
	private int getTabRotation(int index)
	{
		if(index < 7)
			return 0;
		if(index < 15)
			return 1;
		if(index < 23)
			return 2;
		return 3;
	}
	
	private void positionTabButtons()
	{
		int index = 0;
		for(int i = 0; i < this.tabButtons.size(); ++i)
		{
			TabButton thisButton = this.tabButtons.get(i);
			if(thisButton.visible)
			{
				thisButton.reposition(this.getTabPosX(index), this.getTabPosY(index), this.getTabRotation(index));
				index++;
			}
		}
	}
	
	@Override
	public void render(PoseStack matrix, int mouseX, int mouseY, float partialTicks)
	{
		
		this.renderBackground(matrix);
		//Render the background
		RenderSystem.setShaderTexture(0, GUI_TEXTURE);
		this.setColor(this.currentTab().getColor());
		this.blit(matrix, this.guiLeft(), this.guiTop(), 0, 0, this.xSize, this.ySize);
		//Render the tab buttons
		super.render(matrix, mouseX, mouseY, partialTicks);
		//Pre-render the tab
		try {
			this.currentTab().preRender(matrix, mouseX, mouseY, partialTicks);
		} catch(Throwable t) { LightmansCurrency.LogError("Error in Settings Tab pre-render.", t); }
		//Render the renderables
		this.tabWidgets.forEach(widget -> widget.render(matrix, mouseX, mouseY, partialTicks));
		//Post-render the tab
		try {
			this.currentTab().postRender(matrix, mouseX, mouseY, partialTicks);
		} catch(Throwable t) { LightmansCurrency.LogError("Error in Settings Tab post-render.", t); }
		
		//Render the tab button tooltips
		for(int i = 0; i < this.tabButtons.size(); ++i)
		{
			if(this.tabButtons.get(i).isMouseOver(mouseX, mouseY))
				this.renderTooltip(matrix, this.tabButtons.get(i).tab.getTooltip(), mouseX, mouseY);
		}
		
	}
	
	public void setColor(int color)
	{
		float r = (float)(color >> 16 & 255) / 255f;
        float g = (float)(color >> 8 & 255) / 255f;
        float b = (float)(color & 255) / 255f;
        RenderSystem.setShaderColor(r, g, b, 1f);
	}
	
	@Override
	public void tick()
	{
		if(this.getTrader() == null)
		{
			this.minecraft.setScreen(null);
			return;
		}
		if(!this.hasPermission(Permissions.EDIT_SETTINGS))
		{
			this.minecraft.setScreen(null);
			if(this.hasPermission(Permissions.OPEN_STORAGE))
				LightmansCurrencyPacketHandler.instance.sendToServer(new MessageOpenStorage(this.getTrader().getID()));
			else
				LightmansCurrencyPacketHandler.instance.sendToServer(new MessageOpenTrades(this.getTrader().getID()));
			return;
		}
		//Update the tabs visibility
		boolean updateTabs = false;
		for(int i = 0; i < this.tabs.size(); ++i)
		{
			boolean visible = this.tabs.get(i).canOpen();
			if(visible != this.tabButtons.get(i).visible)
			{
				updateTabs = true;
				this.tabButtons.get(i).visible = visible;
			}
		}
		if(updateTabs)
			this.positionTabButtons();
		
		if(!this.currentTab().canOpen())
		{
			this.clickedOnTab(this.tabButtons.get(0));
		}
		
		//Tick the current tab
		try {
			this.currentTab().tick();
		} catch(Throwable t) { LightmansCurrency.LogError("Error in Settings Tab tick.", t); }
		
	}
	
	public boolean hasPermission(String permission)
	{
		if(this.trader.get() != null)
			return this.trader.get().hasPermission(this.getPlayer(), permission);
		return false;
	}
	
	public int getPermissionLevel(String permission)
	{
		if(this.trader.get() != null)
			return this.trader.get().getPermissionLevel(this.getPlayer(), permission);
		return 0;
	}
	
	public boolean hasPermissions(List<String> permissions)
	{
		for(int i = 0; i < permissions.size(); ++i)
		{
			if(!this.hasPermission(permissions.get(i)))
				return false;
		}
		return true;
	}
	
	public <T extends AbstractWidget> T addRenderableTabWidget(T widget)
	{
		this.tabWidgets.add(widget);
		return widget;
	}
	
	public void removeRenderableTabWidget(AbstractWidget widget)
	{
		if(this.tabWidgets.contains(widget))
			this.tabWidgets.remove(widget);
	}
	
	public <T extends GuiEventListener> T addTabListener(T listener)
	{
		this.tabListeners.add(listener);
		return listener;
	}
	
	public void removeTabListener(GuiEventListener listener)
	{
		if(this.tabListeners.contains(listener))
			this.tabListeners.remove(listener);
	}
	
	private void clickedOnTab(Button tab)
	{
		int tabIndex = this.tabButtons.indexOf(tab);
		if(tabIndex < 0)
			return;
		if(tabIndex != this.currentTabIndex)
		{
			//Close the old tab
			try {
				this.currentTab().closeTab();
			} catch(Throwable t) { LightmansCurrency.LogError("Error in Settings Tab close.", t); }
			
			this.tabButtons.get(this.currentTabIndex).active = true;
			this.currentTabIndex = tabIndex;
			this.tabButtons.get(this.currentTabIndex).active = false;
			
			//Clear the previous tabs widgets
			this.tabWidgets.clear();
			this.tabListeners.clear();
			
			//Initialize the new tab
			try {
				this.currentTab().initTab();
			} catch(Throwable t) { LightmansCurrency.LogError("Error in Settings Tab init.", t); }
		}
	}
	
	private void OpenStorage(Button button)
	{
		LightmansCurrencyPacketHandler.instance.sendToServer(new MessageOpenStorage(this.getTrader().getID()));
	}
	
	@Override
	public List<? extends GuiEventListener> children()
	{
		List<? extends GuiEventListener> coreListeners = super.children();
		List<GuiEventListener> listeners = Lists.newArrayList();
		for(int i = 0; i < coreListeners.size(); ++i)
			listeners.add(coreListeners.get(i));
		listeners.addAll(this.tabWidgets);
		listeners.addAll(this.tabListeners);
		return listeners;
	}
	
	@Override
	public boolean isPauseScreen() { return false; }
	
}
