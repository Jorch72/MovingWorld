package darkevilmac.movingworld.common.entity;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import darkevilmac.movingworld.MovingWorld;
import darkevilmac.movingworld.common.chunk.mobilechunk.MobileChunkServer;
import darkevilmac.movingworld.common.network.ChunkBlockUpdateMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkPosition;

import java.util.Collection;

public abstract class MovingWorldHandlerServer extends MovingWorldHandlerCommon {
    protected boolean firstChunkUpdate;

    public MovingWorldHandlerServer(EntityMovingWorld entitymovingWorld) {
        super(entitymovingWorld);
        firstChunkUpdate = true;
    }

    @Override
    public boolean interact(EntityPlayer player) {
        if (getMovingWorld().riddenByEntity == null) {
            player.mountEntity(getMovingWorld());
            return true;
        } else if (player.ridingEntity == null) {
            return getMovingWorld().getCapabilities().mountEntity(player);
        }

        return false;
    }

    @Override
    public void onChunkUpdate() {
        super.onChunkUpdate();
        Collection<ChunkPosition> list = ((MobileChunkServer) getMovingWorld().getMovingWorldChunk()).getSendQueue();
        if (!firstChunkUpdate) {
            ChunkBlockUpdateMessage msg = new ChunkBlockUpdateMessage(getMovingWorld(), list);
            MovingWorld.instance.network.sendToAllAround(msg, new TargetPoint(getMovingWorld().worldObj.provider.dimensionId, getMovingWorld().posX, getMovingWorld().posY, getMovingWorld().posZ, 64D));
        }
        list.clear();
        firstChunkUpdate = false;
    }
}
