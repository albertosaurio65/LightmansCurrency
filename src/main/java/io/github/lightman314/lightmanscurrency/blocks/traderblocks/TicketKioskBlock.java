package io.github.lightman314.lightmanscurrency.blocks.traderblocks;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;

import io.github.lightman314.lightmanscurrency.blockentity.trader.TicketTraderBlockEntity;
import io.github.lightman314.lightmanscurrency.blocks.traderblocks.interfaces.IItemTraderBlock;
import io.github.lightman314.lightmanscurrency.blocks.traderblocks.templates.TraderBlockTallRotatable;
import io.github.lightman314.lightmanscurrency.blocks.util.LazyShapes;
import io.github.lightman314.lightmanscurrency.core.ModBlockEntities;
import io.github.lightman314.lightmanscurrency.items.tooltips.LCTooltips;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.NonNullSupplier;

public class TicketKioskBlock extends TraderBlockTallRotatable implements IItemTraderBlock{
	
	public static final int TRADECOUNT = 4;
	
	private static final VoxelShape HORIZ_SHAPE = box(3d,0d,1d,13d,32d,15d);
	private static final VoxelShape VERT_SHAPE = box(1d,0d,3d,15d,32d,13d);
	
	public TicketKioskBlock(Properties properties)
	{
		super(properties, LazyShapes.lazyTallDirectionalShape(VERT_SHAPE, HORIZ_SHAPE, VERT_SHAPE, HORIZ_SHAPE));
	}
	
	@Override
	public BlockEntity makeTrader(BlockPos pos, BlockState state) { return new TicketTraderBlockEntity(pos, state, TRADECOUNT); }
	
	@Override
	public BlockEntityType<?> traderType() { return ModBlockEntities.TICKET_TRADER.get(); }
	
	@Override
	public List<Vector3f> GetStackRenderPos(int tradeSlot, BlockState state, boolean isDoubleBlock) { return new ArrayList<>(); }
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public List<Quaternion> GetStackRenderRot(int tradeSlot, BlockState state) { return new ArrayList<>(); }

	@Override
	@OnlyIn(Dist.CLIENT)
	public float GetStackRenderScale(int tradeSlot, BlockState state){ return 0f; }
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public int maxRenderIndex() { return -1; }
	
	@Override
	protected NonNullSupplier<List<Component>> getItemTooltips() { return LCTooltips.ITEM_TRADER_TICKET; }
	
}
