package darkevilmac.movingworld.network.advanced;

import darkevilmac.archimedes.ArchimedesShipMod;
import darkevilmac.archimedes.entity.EntityShip;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class MsgRequestShipData extends ASMessageShip {
    public MsgRequestShipData() {
        super();
    }

    public MsgRequestShipData(EntityShip entityship) {
        super(entityship);
    }

    @Override
    public void handleServerSide(EntityPlayer player) {
        if (ship != null) {
            if (ship.getShipChunk().chunkTileEntityMap.isEmpty()) {
                return;
            }

            MsgTileEntities msg = new MsgTileEntities(ship);
            ArchimedesShipMod.instance.pipeline.sendTo(msg, (EntityPlayerMP) player);
        }
    }

    @Override
    public void handleClientSide(EntityPlayer player) {
    }
}