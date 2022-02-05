package io.github.lightman314.lightmanscurrency.client.gui.screen.inventory;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;

import io.github.lightman314.lightmanscurrency.client.gui.screen.TradingTerminalScreen;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.*;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.icon.IconData;
import io.github.lightman314.lightmanscurrency.common.ItemTraderUtil;
import io.github.lightman314.lightmanscurrency.containers.UniversalItemTraderContainer;
import io.github.lightman314.lightmanscurrency.network.LightmansCurrencyPacketHandler;
import io.github.lightman314.lightmanscurrency.network.message.trader.MessageCollectCoins;
import io.github.lightman314.lightmanscurrency.network.message.trader.MessageExecuteTrade;
import io.github.lightman314.lightmanscurrency.network.message.universal_trader.MessageOpenStorage2;
import io.github.lightman314.lightmanscurrency.trader.permissions.Permissions;
import io.github.lightman314.lightmanscurrency.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public class UniversalItemTraderScreen extends ContainerScreen<UniversalItemTraderContainer>{

	public static final ResourceLocation GUI_TEXTURE = new ResourceLocation(LightmansCurrency.MODID, "textures/gui/container/trader.png");
	
	Button buttonShowStorage;
	Button buttonCollectMoney;
	Button buttonBack;
	
	protected List<ItemTradeButton> tradeButtons = new ArrayList<>();
	
	public UniversalItemTraderScreen(UniversalItemTraderContainer container, PlayerInventory inventory, ITextComponent title)
	{
		super(container, inventory, title);
		this.ySize = 133 + ItemTraderUtil.getTradeDisplayHeight(this.container.getData());
		this.xSize = ItemTraderUtil.getWidth(this.container.getData());
	}
	
	@Override
	protected void drawGuiContainerBackgroundLayer(MatrixStack matrix, float partialTicks, int mouseX, int mouseY)
	{
		
		ItemTraderScreen.drawTraderBackground(matrix, this, this.container, this.minecraft, this.xSize, this.ySize, this.container.getData());
		
	}
	
	@Override
	protected void drawGuiContainerForegroundLayer(MatrixStack matrix, int mouseX, int mouseY)
	{
		
		ItemTraderScreen.drawTraderForeground(matrix, this.font, this.container.getData(), this.ySize,
				this.container.getData().getTitle(),
				this.playerInventory.getDisplayName(),
				new TranslationTextComponent("tooltip.lightmanscurrency.credit", MoneyUtil.getStringOfValue(this.container.GetCoinValue())));
		
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		int tradeOffset = ItemTraderUtil.getTradeDisplayOffset(this.container.getData());
		int inventoryOffset = ItemTraderUtil.getInventoryDisplayOffset(this.container.getData());
		
		this.buttonBack = this.addButton(new IconButton(this.guiLeft -20 + inventoryOffset, this.guiTop + this.ySize - 20, this::PressBackButton, this.font, IconData.of(GUI_TEXTURE, 176 + 32, 0)));
		
		this.buttonShowStorage = this.addButton(new IconButton(this.guiLeft - 20 + tradeOffset, this.guiTop, this::PressStorageButton, this.font, IconData.of(Items.CHEST)));
		this.buttonShowStorage.visible = this.container.hasPermissions(Permissions.OPEN_STORAGE);
		
		this.buttonCollectMoney = this.addButton(new IconButton(this.guiLeft - 20 + tradeOffset, this.guiTop + 20, this::PressCollectionButton, this.font, IconData.of(GUI_TEXTURE, 176 + 16, 0)));
		this.buttonCollectMoney.active = false;
		this.buttonCollectMoney.visible = this.container.hasPermissions(Permissions.COLLECT_COINS) && !this.container.getData().getCoreSettings().hasBankAccount();
		
		initTradeButtons();
		
	}
	
	protected void initTradeButtons()
	{
		int tradeCount = this.container.getTradeCount();
		for(int i = 0; i < tradeCount; i++)
		{
			this.tradeButtons.add(this.addButton(new ItemTradeButton(this.guiLeft + ItemTraderUtil.getButtonPosX(this.container.getData(), i), this.guiTop + ItemTraderUtil.getButtonPosY(this.container.getData(), i), this::PressTradeButton, i, this, this.font, () -> this.container.getData(), () -> this.container.GetCoinValue(), () -> this.container.GetItemInventory())));
		}
	}
	
	@Override
	public void tick()
	{
		super.tick();
		
		this.container.tick();
		
		this.buttonShowStorage.visible = this.container.hasPermissions(Permissions.OPEN_STORAGE);
		
		if(this.container.hasPermissions(Permissions.COLLECT_COINS))
		{
			this.buttonCollectMoney.visible = !this.container.getData().getCoreSettings().hasBankAccount();;
			this.buttonCollectMoney.active = this.container.getData().getStoredMoney().getRawValue() > 0;
			if(!this.buttonCollectMoney.active)
				this.buttonCollectMoney.visible = !this.container.getData().getCoreSettings().isCreative();
		}
		else
			this.buttonCollectMoney.visible = false;
		
	}
	
	@Override
	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
	{
		this.renderBackground(matrixStack);
		super.render(matrixStack, mouseX, mouseY, partialTicks);
		this.renderHoveredTooltip(matrixStack, mouseX,  mouseY);
		
		if(this.buttonShowStorage != null && this.buttonShowStorage.isMouseOver(mouseX,mouseY))
		{
			this.renderTooltip(matrixStack, new TranslationTextComponent("tooltip.lightmanscurrency.trader.openstorage"), mouseX, mouseY);
		}
		else if(this.buttonCollectMoney != null && this.buttonCollectMoney.active && this.buttonCollectMoney.isMouseOver(mouseX, mouseY))
		{
			this.renderTooltip(matrixStack, new TranslationTextComponent("tooltip.lightmanscurrency.trader.collectcoins", this.container.getData().getStoredMoney().getString()), mouseX, mouseY);
		}
		else if(this.buttonBack != null && this.buttonBack.active && this.buttonBack.isMouseOver(mouseX, mouseY))
		{
			this.renderTooltip(matrixStack, new TranslationTextComponent("tooltip.lightmanscurrency.trader.universaltrader.back"), mouseX, mouseY);
		}
		for(int i = 0; i < this.tradeButtons.size(); i++)
		{
			this.tradeButtons.get(i).tryRenderTooltip(matrixStack, this, this.container.getData(), false, mouseX, mouseY);
		}
		
	}
	
	private void PressStorageButton(Button button)
	{
		//Open the container screen
		if(this.container.hasPermissions(Permissions.OPEN_STORAGE))
		{
			//CurrencyMod.LOGGER.info("Owner attempted to open the Trader's Storage.");
			LightmansCurrencyPacketHandler.instance.sendToServer(new MessageOpenStorage2(this.container.getData().getTraderID()));
		}
	}
	
	private void PressCollectionButton(Button button)
	{
		//Open the container screen
		if(this.container.hasPermissions(Permissions.COLLECT_COINS))
		{
			//CurrencyMod.LOGGER.info("Owner attempted to collect the stored money.");
			LightmansCurrencyPacketHandler.instance.sendToServer(new MessageCollectCoins());
		}
	}
	
	private void PressTradeButton(Button button)
	{
		
		int tradeIndex = 0;
		if(tradeButtons.contains(button))
			tradeIndex = tradeButtons.indexOf(button);
		
		LightmansCurrencyPacketHandler.instance.sendToServer(new MessageExecuteTrade(tradeIndex));
		
	}
	
	private void PressBackButton(Button button)
	{
		this.minecraft.displayGuiScreen(new TradingTerminalScreen());
	}
	
}
