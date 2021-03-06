package darkevilmac.movingworld.common.chunk.mobilechunk;

import darkevilmac.movingworld.common.entity.EntityMovingWorld;
import net.minecraft.block.Block;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MobileChunkServer extends MobileChunk {
    private Set<ChunkPosition> sendQueue;

    public MobileChunkServer(World world, EntityMovingWorld entityMovingWorld) {
        super(world, entityMovingWorld);
        sendQueue = new HashSet<ChunkPosition>();
    }

    public Collection<ChunkPosition> getSendQueue() {
        return sendQueue;
    }

    @Override
    public boolean setBlockIDWithMetadata(int x, int y, int z, Block block, int meta) {
        if (super.setBlockIDWithMetadata(x, y, z, block, meta)) {
            sendQueue.add(new ChunkPosition(x, y, z));
            return true;
        }
        return false;
    }

    @Override
    public boolean setBlockMetadata(int x, int y, int z, int meta) {
        if (super.setBlockMetadata(x, y, z, meta)) {
            sendQueue.add(new ChunkPosition(x, y, z));
            return true;
        }
        return false;
    }

    @Override
    protected void onSetBlockAsFilledAir(int x, int y, int z) {
    }
}
