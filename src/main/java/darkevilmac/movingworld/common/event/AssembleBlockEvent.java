package darkevilmac.movingworld.common.event;

import cpw.mods.fml.common.eventhandler.Event;
import darkevilmac.movingworld.common.chunk.LocatedBlock;

/**
 * Created by DarkEvilMac on 2/22/2015.
 */

public class AssembleBlockEvent extends Event {

    public LocatedBlock block;

    public AssembleBlockEvent(LocatedBlock block) {
        this.block = block;
    }

}