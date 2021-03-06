package darkevilmac.movingworld.common.chunk.assembly;

import darkevilmac.movingworld.MovingWorld;
import darkevilmac.movingworld.common.block.BlockMovingWorldMarker;
import darkevilmac.movingworld.common.chunk.LocatedBlock;
import darkevilmac.movingworld.common.chunk.MovingWorldSizeOverflowException;
import darkevilmac.movingworld.common.tile.TileMovingWorldMarkingBlock;
import net.minecraft.block.Block;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ChunkAssembler {
    public final int startX, startY, startZ;
    private final int maxBlocks;
    private World worldObj;

    public ChunkAssembler(World world, int x, int y, int z, int maxMovingWorldBlocks) {
        worldObj = world;

        startX = x;
        startY = y;
        startZ = z;

        maxBlocks = maxMovingWorldBlocks;
    }

    public AssembleResult doAssemble(MovingWorldAssemblyInteractor interactor) {
        AssembleResult result = new AssembleResult();
        result.xOffset = startX;
        result.yOffset = startY;
        result.zOffset = startZ;
        result.assemblyInteractor = interactor;
        try {
            if (MovingWorld.instance.mConfig.iterativeAlgorithm) {
                assembleIterative(result, result.assemblyInteractor, startX, startY, startZ);
            } else {
                assembleRecursive(result, new HashSet<ChunkPosition>(), result.assemblyInteractor, startX, startY, startZ);
            }
            if (result.movingWorldMarkingBlock == null) {
                result.resultCode = AssembleResult.RESULT_MISSING_MARKER;
            } else {
                result.resultCode = AssembleResult.RESULT_OK;
            }
        } catch (MovingWorldSizeOverflowException e) {
            result.resultCode = AssembleResult.RESULT_BLOCK_OVERFLOW;
        } catch (Error e) {
            result.resultCode = AssembleResult.RESULT_ERROR_OCCURED;
        }
        result.assemblyInteractor.chunkAssembled(result);
        return result;
    }

    private void assembleIterative(AssembleResult result, MovingWorldAssemblyInteractor assemblyInteractor, int sX, int sY, int sZ) throws MovingWorldSizeOverflowException {
        HashSet<ChunkPosition> openSet = new HashSet<ChunkPosition>();
        HashSet<ChunkPosition> closedSet = new HashSet<ChunkPosition>();
        List<ChunkPosition> iterator = new ArrayList<ChunkPosition>();

        LocatedBlock movingWorldMarker = null;

        int x = sX, y = sY, z = sZ;

        openSet.add(new ChunkPosition(sX, sY, sZ));
        while (!openSet.isEmpty()) {
            iterator.addAll(openSet);
            for (ChunkPosition pos : iterator) {
                openSet.remove(pos);

                if (closedSet.contains(pos)) {
                    continue;
                }
                if (result.assembledBlocks.size() > maxBlocks) {
                    throw new MovingWorldSizeOverflowException();
                }

                x = pos.chunkPosX;
                y = pos.chunkPosY;
                z = pos.chunkPosZ;

                closedSet.add(pos);

                Block block = worldObj.getBlock(x, y, z);
                CanAssemble canAssemble = canUseBlockForVehicle(block, assemblyInteractor, x, y, z);

                if (canAssemble.justCancel) {
                    continue;
                }

                LocatedBlock lb = new LocatedBlock(block, worldObj.getBlockMetadata(x, y, z), worldObj.getTileEntity(x, y, z), pos, null);
                assemblyInteractor.blockAssembled(lb);
                if ((lb.block != null && lb.block instanceof BlockMovingWorldMarker) || (lb.tileEntity != null && lb.tileEntity instanceof TileMovingWorldMarkingBlock)) {
                    if (movingWorldMarker == null)
                        movingWorldMarker = lb;
                }
                result.assembleBlock(lb);

                if (!canAssemble.assembleThenCancel) {
                    openSet.add(new ChunkPosition(x - 1, y, z));
                    openSet.add(new ChunkPosition(x, y - 1, z));
                    openSet.add(new ChunkPosition(x, y, z - 1));
                    openSet.add(new ChunkPosition(x + 1, y, z));
                    openSet.add(new ChunkPosition(x, y + 1, z));
                    openSet.add(new ChunkPosition(x, y, z + 1));

                    if (assemblyInteractor.doDiagonalAssembly()) {
                        openSet.add(new ChunkPosition(x - 1, y - 1, z));
                        openSet.add(new ChunkPosition(x + 1, y - 1, z));
                        openSet.add(new ChunkPosition(x + 1, y + 1, z));
                        openSet.add(new ChunkPosition(x - 1, y + 1, z));

                        openSet.add(new ChunkPosition(x - 1, y, z - 1));
                        openSet.add(new ChunkPosition(x + 1, y, z - 1));
                        openSet.add(new ChunkPosition(x + 1, y, z + 1));
                        openSet.add(new ChunkPosition(x - 1, y, z + 1));

                        openSet.add(new ChunkPosition(x, y - 1, z - 1));
                        openSet.add(new ChunkPosition(x, y + 1, z - 1));
                        openSet.add(new ChunkPosition(x, y + 1, z + 1));
                        openSet.add(new ChunkPosition(x, y - 1, z + 1));
                    }
                }
            }
        }
        result.movingWorldMarkingBlock = movingWorldMarker;
    }

    private void assembleRecursive(AssembleResult result, HashSet<ChunkPosition> set, MovingWorldAssemblyInteractor assemblyInteractor, int x, int y, int z) throws MovingWorldSizeOverflowException {
        LocatedBlock movingWorldMarker = null;

        if (result.assembledBlocks.size() > maxBlocks) {
            throw new MovingWorldSizeOverflowException();
        }

        ChunkPosition pos = new ChunkPosition(x, y, z);
        if (set.contains(pos)) return;

        set.add(pos);
        Block block = worldObj.getBlock(x, y, z);

        CanAssemble canAssemble = canUseBlockForVehicle(block, assemblyInteractor, x, y, z);

        if (canAssemble.justCancel) {
            return;
        }

        LocatedBlock lb = new LocatedBlock(block, worldObj.getBlockMetadata(x, y, z), worldObj.getTileEntity(x, y, z), pos, null);
        assemblyInteractor.blockAssembled(lb);
        if ((lb.block != null && lb.block instanceof BlockMovingWorldMarker) || (lb.tileEntity != null && lb.tileEntity instanceof TileMovingWorldMarkingBlock)) {
            if (movingWorldMarker == null)
                movingWorldMarker = lb;
        }
        result.assembleBlock(lb);

        if (!canAssemble.assembleThenCancel) {
            assembleRecursive(result, set, assemblyInteractor, x - 1, y, z);
            assembleRecursive(result, set, assemblyInteractor, x, y - 1, z);
            assembleRecursive(result, set, assemblyInteractor, x, y, z - 1);
            assembleRecursive(result, set, assemblyInteractor, x + 1, y, z);
            assembleRecursive(result, set, assemblyInteractor, x, y + 1, z);
            assembleRecursive(result, set, assemblyInteractor, x, y, z + 1);

            if (assemblyInteractor.doDiagonalAssembly()) {
                assembleRecursive(result, set, assemblyInteractor, x - 1, y - 1, z);
                assembleRecursive(result, set, assemblyInteractor, x + 1, y - 1, z);
                assembleRecursive(result, set, assemblyInteractor, x + 1, y + 1, z);
                assembleRecursive(result, set, assemblyInteractor, x - 1, y + 1, z);

                assembleRecursive(result, set, assemblyInteractor, x - 1, y, z - 1);
                assembleRecursive(result, set, assemblyInteractor, x + 1, y, z - 1);
                assembleRecursive(result, set, assemblyInteractor, x + 1, y, z + 1);
                assembleRecursive(result, set, assemblyInteractor, x - 1, y, z + 1);

                assembleRecursive(result, set, assemblyInteractor, x, y - 1, z - 1);
                assembleRecursive(result, set, assemblyInteractor, x, y + 1, z - 1);
                assembleRecursive(result, set, assemblyInteractor, x, y + 1, z + 1);
                assembleRecursive(result, set, assemblyInteractor, x, y - 1, z + 1);
            }
        }
        result.movingWorldMarkingBlock = movingWorldMarker;
    }

    public CanAssemble canUseBlockForVehicle(Block block, MovingWorldAssemblyInteractor assemblyInteractor, int x, int y, int z) {
        return assemblyInteractor.isBlockAllowed(worldObj, block, x, y, z);
    }

}
