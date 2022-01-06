package io.github.lightman314.lightmanscurrency.blocks.traderblocks;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;

import io.github.lightman314.lightmanscurrency.blockentity.ItemInterfaceBlockEntity;
import io.github.lightman314.lightmanscurrency.blockentity.ItemInterfaceBlockEntity.IItemHandlerBlock;
import io.github.lightman314.lightmanscurrency.blockentity.ItemInterfaceBlockEntity.IItemHandlerBlockEntity;
import io.github.lightman314.lightmanscurrency.blockentity.TicketTraderBlockEntity;
import io.github.lightman314.lightmanscurrency.blocks.traderblocks.interfaces.IItemTraderBlock;
import io.github.lightman314.lightmanscurrency.blocks.traderblocks.templates.TraderBlockTallRotatable;
import io.github.lightman314.lightmanscurrency.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class TicketKioskBlock extends TraderBlockTallRotatable implements IItemTraderBlock{
	
	public static final int TRADECOUNT = 4;
	
	private static final VoxelShape SHAPE = box(3d,0d,3d,13d,32d,13d);
	
	public TicketKioskBlock(Properties properties)
	{
		super(properties, SHAPE);
	}
	
	@Override
	public BlockEntity makeTrader(BlockPos pos, BlockState state) { return new TicketTraderBlockEntity(pos, state, TRADECOUNT); }
	
	@Override
	protected BlockEntity makeDummy(BlockPos pos, BlockState state) { return new ItemInterfaceBlockEntity(pos, state); }
	
	@Override
	public BlockEntityType<?> traderType() { return ModBlockEntities.ITEM_TRADER; }
	
	@Override
	public List<Vector3f> GetStackRenderPos(int tradeSlot, BlockState state, boolean isBlock) {
		return new ArrayList<>();
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public List<Quaternion> GetStackRenderRot(int tradeSlot, BlockState state, boolean isBlock)
	{
		return new ArrayList<>();
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public Vector3f GetStackRenderScale(int tradeSlot, BlockState state, boolean isBlock){
		return new Vector3f(1f,1f,1f);
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public int maxRenderIndex()
	{
		return -1;
	}
	
	@Override
	public Direction getRelativeSide(BlockState state, Direction side) {
		return IItemHandlerBlock.getRelativeSide(this.getFacing(state), side);
	}

	@Override
	public IItemHandlerBlockEntity getItemHandlerEntity(BlockState state, Level level, BlockPos pos) {
		BlockEntity blockEntity = this.getBlockEntity(state, level, pos);
		if(blockEntity instanceof IItemHandlerBlockEntity)
			return (IItemHandlerBlockEntity)blockEntity;
		return null;
	}
	
}
