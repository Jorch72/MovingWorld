package darkevilmac.movingworld.common.entity;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import darkevilmac.movingworld.MovingWorld;
import darkevilmac.movingworld.common.chunk.ChunkIO;
import darkevilmac.movingworld.common.chunk.LocatedBlock;
import darkevilmac.movingworld.common.chunk.MovingWorldSizeOverflowException;
import darkevilmac.movingworld.common.chunk.assembly.ChunkDisassembler;
import darkevilmac.movingworld.common.chunk.assembly.MovingWorldAssemblyInteractor;
import darkevilmac.movingworld.common.chunk.mobilechunk.MobileChunk;
import darkevilmac.movingworld.common.chunk.mobilechunk.MobileChunkClient;
import darkevilmac.movingworld.common.chunk.mobilechunk.MobileChunkServer;
import darkevilmac.movingworld.common.util.AABBRotator;
import darkevilmac.movingworld.common.util.MathHelperMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * All moving sections of blocks extend from this class.
 */
public abstract class EntityMovingWorld extends EntityBoat implements IEntityAdditionalSpawnData {

    public float motionYaw;
    public int frontDirection;
    public int riderDestinationX, riderDestinationY, riderDestinationZ;
    public boolean isFlying;
    public Entity prevRiddenByEntity;
    protected float groundFriction, horFriction, vertFriction;
    protected int[] layeredBlockVolumeCount;
    private MobileChunk mobileChunk;
    private MovingWorldInfo info;
    private ChunkDisassembler disassembler;

    // Related to actual movement. We don't ever really change this variables, they're changed by classes derived from EntityMovingWorld
    private boolean noControl;
    private boolean syncPosWithServer;
    @SideOnly(Side.CLIENT)
    private double controlPosRotationIncrements;
    @SideOnly(Side.CLIENT)
    private double controlX, controlY, controlZ;
    @SideOnly(Side.CLIENT)
    private double controlPitch, controlYaw;
    @SideOnly(Side.CLIENT)
    private double controlVelX, controlVelY, controlVelZ;

    public EntityMovingWorld(World world) {
        super(world);
        info = new MovingWorldInfo();
        if (world.isRemote) {
            initClient();
        } else {
            initCommon();
        }

        motionYaw = 0F;

        layeredBlockVolumeCount = null;
        frontDirection = 0;
        yOffset = 0F;

        groundFriction = 0.9F;
        horFriction = 0.994F;
        vertFriction = 0.95F;

        prevRiddenByEntity = null;

        isFlying = false;
    }

    public static boolean isAABBInLiquidNotFall(World world, AxisAlignedBB aabb) {
        int i = MathHelper.floor_double(aabb.minX);
        int j = MathHelper.floor_double(aabb.maxX + 1D);
        int k = MathHelper.floor_double(aabb.minY);
        int l = MathHelper.floor_double(aabb.maxY + 1D);
        int i1 = MathHelper.floor_double(aabb.minZ);
        int j1 = MathHelper.floor_double(aabb.maxZ + 1D);

        for (int x = i; x < j; ++x) {
            for (int y = k; y < l; ++y) {
                for (int z = i1; z < j1; ++z) {
                    Block block = world.getBlock(x, y, z);

                    if (block != null && (block.getMaterial() == Material.water || block.getMaterial() == Material.lava)) {
                        int j2 = world.getBlockMetadata(x, y, z);
                        double d0;

                        if (j2 < 8) {
                            d0 = y + 1 - j2 / 8.0D;

                            if (d0 >= aabb.minY) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float damage) {
        return false;
    }

    public abstract MovingWorldHandlerCommon getHandler();

    public int[] getLayeredBlockVolumeCount() {
        return layeredBlockVolumeCount;
    }

    public void setLayeredBlockVolumeCount(int[] layeredBlockVolumeCount) {
        this.layeredBlockVolumeCount = layeredBlockVolumeCount;
    }

    @SideOnly(Side.CLIENT)
    private void initClient() {
        mobileChunk = new MobileChunkClient(worldObj, this);
        initMovingWorldClient();
    }

    private void initCommon() {
        mobileChunk = new MobileChunkServer(worldObj, this);
        initMovingWorldCommon();
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(30, 0);
        initMovingWorld();
    }

    public abstract void initMovingWorld();

    public abstract void initMovingWorldClient();

    public abstract void initMovingWorldCommon();

    public MobileChunk getMovingWorldChunk() {
        return mobileChunk;
    }

    public abstract MovingWorldCapabilities getCapabilities();

    public abstract void setCapabilities(MovingWorldCapabilities capabilities);

    public ChunkDisassembler getDisassembler() {
        if (disassembler == null) {
            disassembler = new ChunkDisassembler(this);
        }
        return disassembler;
    }

    public MovingWorldInfo getInfo() {
        return info;
    }

    public void setInfo(MovingWorldInfo movingWorldInfo) {
        if (movingWorldInfo == null) throw new NullPointerException("Cannot set null moving world info");
        info = movingWorldInfo;
    }

    @Override
    public boolean interactFirst(EntityPlayer entityplayer) {
        return getHandler().interact(entityplayer);
    }

    @Override
    public void setDead() {
        super.setDead();
        mobileChunk.onChunkUnload();
        getCapabilities().clear();
    }

    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();
        if (mobileChunk.isModified) {
            mobileChunk.isModified = false;
            getHandler().onChunkUpdate();
        }
    }

    public void setRotatedBoundingBox() {
        if (mobileChunk == null) {
            float hw = width / 2F;
            boundingBox.setBounds(posX - hw, posY, posZ - hw, posX + hw, posY + height, posZ + hw);
        } else {
            boundingBox.setBounds(posX - mobileChunk.getCenterX(), posY, posZ - mobileChunk.getCenterZ(), posX + mobileChunk.getCenterX(), posY + height, posZ + mobileChunk.getCenterZ());
            AABBRotator.rotateAABBAroundY(boundingBox, posX, posZ, (float) Math.toRadians(rotationYaw));
        }
    }

    @Override
    public void setSize(float w, float h) {
        if (w != width || h != height) {
            width = w;
            height = h;
            float hw = w / 2F;
            boundingBox.setBounds(posX - hw, posY, posZ - hw, posX + hw, posY + height, posZ + hw);
        }

        float f = w % 2.0F;
        if (f < 0.375D) {
            myEntitySize = EnumEntitySize.SIZE_1;
        } else if (f < 0.75D) {
            myEntitySize = EnumEntitySize.SIZE_2;
        } else if (f < 1.0D) {
            myEntitySize = EnumEntitySize.SIZE_3;
        } else if (f < 1.375D) {
            myEntitySize = EnumEntitySize.SIZE_4;
        } else if (f < 1.75D) {
            myEntitySize = EnumEntitySize.SIZE_5;
        } else {
            myEntitySize = EnumEntitySize.SIZE_6;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int incr) {
        if (noControl) {
            controlPosRotationIncrements = incr + 5;
        } else {
            double dx = x - posX;
            double dy = y - posY;
            double dz = z - posZ;
            double d = dx * dx + dy * dy + dz * dz;

            if (d < 0.3D) {
                return;
            }

            syncPosWithServer = true;
            controlPosRotationIncrements = incr;
        }

        controlX = x;
        controlY = y;
        controlZ = z;
        controlYaw = yaw;
        controlPitch = pitch;
        motionX = controlVelX;
        motionY = controlVelY;
        motionZ = controlVelZ;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setVelocity(double x, double y, double z) {
        controlVelX = motionX = x;
        controlVelY = motionY = y;
        controlVelZ = motionZ = z;
    }

    @Override
    public void onUpdate() {
        onEntityUpdate();
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;

        double horvel = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (worldObj.isRemote) {
            if (riddenByEntity == null) {
                setIsBoatEmpty(true);
            }
            spawnParticles(horvel);
        }

        if (worldObj.isRemote && (noControl || syncPosWithServer)) {
            handleClientUpdate();
            if (controlPosRotationIncrements == 0) {
                syncPosWithServer = false;
            }
        } else {
            handleServerUpdate(horvel);
        }
    }

    @SideOnly(Side.CLIENT)
    protected void handleClientUpdate() {
        if (controlPosRotationIncrements > 0) {
            double dx = posX + (controlX - posX) / controlPosRotationIncrements;
            double dy = posY + (controlY - posY) / controlPosRotationIncrements;
            double dz = posZ + (controlZ - posZ) / controlPosRotationIncrements;
            double ang = MathHelper.wrapAngleTo180_double(controlYaw - rotationYaw);
            rotationYaw = (float) (rotationYaw + ang / controlPosRotationIncrements);
            rotationPitch = (float) (rotationPitch + (controlPitch - rotationPitch) / controlPosRotationIncrements);
            controlPosRotationIncrements--;
            setPosition(dx, dy, dz);
            setRotation(rotationYaw, rotationPitch);
        } else {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);

            if (onGround) {
                motionX *= groundFriction;
                motionY *= groundFriction;
                motionZ *= groundFriction;
            }

            motionX *= horFriction;
            motionY *= vertFriction;
            motionZ *= horFriction;
        }
        setRotatedBoundingBox();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setIsBoatEmpty(boolean flag) {
        noControl = flag;
    }

    protected void handleServerUpdate(double horvel) {
        //START outer forces
        float gravity = 0.05F;
        if (!isFlying()) {
            motionY -= gravity;
        }
        //END outer forces

        handleControl(horvel);

        //START limit motion
        double newhorvel = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double maxvel = getCapabilities().getSpeedLimit();
        if (newhorvel > maxvel) {
            double d = maxvel / newhorvel;
            motionX *= d;
            motionZ *= d;
            newhorvel = maxvel;
        }
        motionY = MathHelperMod.clamp_double(motionY, -maxvel, maxvel);
        //END limit motion

        if (onGround) {
            motionX *= groundFriction;
            motionY *= groundFriction;
            motionZ *= groundFriction;
        }
        rotationPitch = rotationPitch + (motionYaw * getCapabilities().getBankingMultiplier() - rotationPitch) * 0.15f;
        motionYaw *= 0.7F;
        //motionYaw = MathHelper.clamp_float(motionYaw, -BASE_TURN_SPEED * ShipMod.instance.modConfig.turnSpeed, BASE_TURN_SPEED * ShipMod.instance.modConfig.turnSpeed);
        rotationYaw += motionYaw;
        setRotatedBoundingBox();
        moveEntity(motionX, motionY, motionZ);
        posY = Math.min(posY, worldObj.getHeight());
        motionX *= horFriction;
        motionY *= vertFriction;
        motionZ *= horFriction;

        handleServerUpdatePreRotation();

        setRotation(rotationYaw, rotationPitch);

        handleCollision(posX, posY, posZ);
    }

    public void handleServerUpdatePreRotation() {
        //No implementation basically just a hook for archimedes ships.

        // dis mai code i do wut i wan
    }

    @Override
    public void updateRiderPosition() {
        updateRiderPosition(riddenByEntity, riderDestinationX, riderDestinationY, riderDestinationZ, 1);
    }

    public void updateRiderPosition(Entity entity, int riderDestinationX, int riderDestinationY, int riderDestinationZ, int flags) {
        if (entity != null) {
            float yaw = (float) Math.toRadians(rotationYaw);
            float pitch = (float) Math.toRadians(rotationPitch);

            int x1 = riderDestinationX, y1 = riderDestinationY, z1 = riderDestinationZ;
            if ((flags & 1) == 1) {
                if (frontDirection == 0) {
                    z1 -= 1;
                } else if (frontDirection == 1) {
                    x1 += 1;
                } else if (frontDirection == 2) {
                    z1 += 1;
                } else if (frontDirection == 3) {
                    x1 -= 1;
                }

                Block block = mobileChunk.getBlock(x1, MathHelper.floor_double(y1 + getMountedYOffset() + entity.getYOffset()), z1);
                if (block.isOpaqueCube()) {
                    x1 = riderDestinationX;
                    y1 = riderDestinationY;
                    z1 = riderDestinationZ;
                }
            }

            double yoff = (flags & 2) == 2 ? 0d : getMountedYOffset();
            Vec3 vec = Vec3.createVectorHelper(x1 - mobileChunk.getCenterX() + 0.5d, y1 - mobileChunk.minY() + yoff, z1 - mobileChunk.getCenterZ() + 0.5d);
            switch (frontDirection) {
                case 0:
                    vec.rotateAroundZ(-pitch);
                    break;
                case 1:
                    vec.rotateAroundX(pitch);
                    break;
                case 2:
                    vec.rotateAroundZ(pitch);
                    break;
                case 3:
                    vec.rotateAroundX(-pitch);
                    break;
            }
            vec.rotateAroundY(yaw);

            entity.setPosition(posX + vec.xCoord, posY + vec.yCoord + entity.getYOffset(), posZ + vec.zCoord);
        }
    }

    private boolean handleCollision(double cPosX, double cPosY, double cPosZ) {
        boolean didCollide = false;
        if (!worldObj.isRemote) {
            @SuppressWarnings("unchecked")
            List<Entity> list = worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(0.2D, 0.0D, 0.2D));
            if (list != null && !list.isEmpty()) {
                didCollide = true;
                for (Entity entity : list) {
                    if (entity != riddenByEntity && entity.canBePushed()) {
                        if (entity instanceof EntityMovingWorld) {
                            entity.applyEntityCollision(this);
                        } else if (entity instanceof EntityBoat) {
                            double d0 = cPosX - entity.posX;
                            double d1 = cPosZ - entity.posZ;
                            double d2 = MathHelper.abs_max(d0, d1);

                            if (d2 >= 0.01D) {
                                d2 = MathHelper.sqrt_double(d2);
                                d0 /= d2;
                                d1 /= d2;
                                double d3 = 1.0D / d2;

                                if (d3 > 1.0D) {
                                    d3 = 1.0D;
                                }

                                d0 *= d3;
                                d1 *= d3;
                                d0 *= 0.05D;
                                d1 *= 0.05D;
                                d0 *= 1.0F - entity.entityCollisionReduction;
                                d1 *= 1.0F - entity.entityCollisionReduction;
                                entity.addVelocity(-d0, 0.0D, -d1);
                            }
                        }
                    }
                }
            }

            for (int l = 0; l < 4; ++l) {
                int i1 = MathHelper.floor_double(cPosX + ((l % 2) - 0.5D) * 0.8D);
                int j1 = MathHelper.floor_double(cPosZ + ((l / 2) - 0.5D) * 0.8D);

                for (int k1 = 0; k1 < 2; ++k1) {
                    int l1 = MathHelper.floor_double(cPosY) + k1;
                    Block block = worldObj.getBlock(i1, l1, j1);

                    if (block == Blocks.snow) {
                        worldObj.setBlockToAir(i1, l1, j1);
                        isCollidedHorizontally = false;
                    } else if (block == Blocks.waterlily) {
                        worldObj.func_147480_a(i1, l1, j1, true);
                        isCollidedHorizontally = false;
                    } else {
                        didCollide = true;
                    }
                }

            }
        }
        return didCollide;
    }

    @Override
    public boolean handleWaterMovement() {
        float f = width;
        width = 0F;
        boolean ret = super.handleWaterMovement();
        width = f;
        return ret;
    }

    public boolean isFlying() {
        return getCapabilities().canFly() && isFlying;
    }

    public abstract boolean isBraking();

    /**
     * Determines whether the entity should be pushed by fluids
     */
    @Override
    public boolean isPushedByWater() {
        return ticksExisted > 60;
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    public double getMountedYOffset() {
        return yOffset + 0.5D;
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBox(Entity entity) {
        return entity instanceof EntityLiving ? null : entity.boundingBox;
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return boundingBox;
    }

    @Override
    public boolean canBePushed() {
        return onGround && !isInWater() && riddenByEntity == null;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Override
    protected void updateFallState(double distancefallen, boolean onground) {
        if (!isFlying()) {

        }
    }

    @Override
    protected void fall(float distance) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getShadowSize() {
        return 0.5F;
    }

    public float getHorizontalVelocity() {
        return (float) Math.sqrt(motionX * motionX + motionZ * motionZ);
    }

    public void alignToGrid() {
        rotationYaw = Math.round(rotationYaw / 90F) * 90F;
        rotationPitch = 0F;

        Vec3 vec = Vec3.createVectorHelper(-mobileChunk.getCenterX(), -mobileChunk.minY(), -mobileChunk.getCenterZ());
        vec.rotateAroundY((float) Math.toRadians(rotationYaw));

        int ix = MathHelperMod.round_double(vec.xCoord + posX);
        int iy = MathHelperMod.round_double(vec.yCoord + posY);
        int iz = MathHelperMod.round_double(vec.zCoord + posZ);

        posX = ix - vec.xCoord;
        posY = iy - vec.yCoord;
        posZ = iz - vec.zCoord;

        motionX = motionY = motionZ = 0D;
    }

    public boolean disassemble(boolean overwrite) {
        if (worldObj.isRemote) return true;

        updateRiderPosition();

        ChunkDisassembler disassembler = getDisassembler();
        disassembler.overwrite = overwrite;

        if (!disassembler.canDisassemble(getAssemblyInteractor())) {
            return false;
        } else {
            disassembler.doDisassemble(getAssemblyInteractor());
        }

        return true;
    }

    public abstract MovingWorldAssemblyInteractor getNewAssemblyInteractor();

    public void dropAsItems() {
        TileEntity tileentity;
        Block block;
        for (int i = mobileChunk.minX(); i < mobileChunk.maxX(); i++) {
            for (int j = mobileChunk.minY(); j < mobileChunk.maxY(); j++) {
                for (int k = mobileChunk.minZ(); k < mobileChunk.maxZ(); k++) {
                    tileentity = mobileChunk.getTileEntity(i, j, k);
                    if (tileentity instanceof IInventory) {
                        IInventory inv = (IInventory) tileentity;
                        for (int it = 0; it < inv.getSizeInventory(); it++) {
                            ItemStack is = inv.getStackInSlot(it);
                            if (is != null) {
                                entityDropItem(is, 0F);
                            }
                        }
                    }
                    block = mobileChunk.getBlock(i, j, k);

                    if (block != Blocks.air) {
                        int meta = mobileChunk.getBlockMetadata(i, j, k);
                        block.dropBlockAsItem(worldObj, MathHelper.floor_double(posX), MathHelper.floor_double(posY), MathHelper.floor_double(posZ), meta, 0);
                    }
                }
            }
        }
    }

    protected void fillAirBlocks(Set<ChunkPosition> set, int x, int y, int z) {
        if (x < mobileChunk.minX() - 1 || x > mobileChunk.maxX() || y < mobileChunk.minY() - 1 || y > mobileChunk.maxY() || z < mobileChunk.minZ() - 1 || z > mobileChunk.maxZ())
            return;
        ChunkPosition pos = new ChunkPosition(x, y, z);
        if (set.contains(pos)) return;

        set.add(pos);
        if (mobileChunk.setBlockAsFilledAir(x, y, z)) {
            fillAirBlocks(set, x, y + 1, z);
            //fillAirBlocks(set, x, y - 1, z);
            fillAirBlocks(set, x - 1, y, z);
            fillAirBlocks(set, x, y, z - 1);
            fillAirBlocks(set, x + 1, y, z);
            fillAirBlocks(set, x, y, z + 1);
        }
    }

    public void setPilotSeat(int frontDirection, int riderDestinationX, int riderDestinationY, int riderDestinationZ) {
        this.frontDirection = frontDirection;
        this.riderDestinationX = riderDestinationX;
        this.riderDestinationY = riderDestinationY;
        this.riderDestinationZ = riderDestinationZ;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(mobileChunk.getMemoryUsage());
        DataOutputStream out = new DataOutputStream(baos);
        try {
            ChunkIO.writeAll(out, mobileChunk);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        compound.setByteArray("chunk", baos.toByteArray());
        compound.setByte("riderDestinationX", (byte) riderDestinationX);
        compound.setByte("riderDestinationY", (byte) riderDestinationY);
        compound.setByte("riderDestinationZ", (byte) riderDestinationZ);
        compound.setByte("front", (byte) frontDirection);

        if (!mobileChunk.chunkTileEntityMap.isEmpty()) {
            NBTTagList tileEntities = new NBTTagList();
            for (TileEntity tileentity : mobileChunk.chunkTileEntityMap.values()) {
                NBTTagCompound comp = new NBTTagCompound();
                tileentity.writeToNBT(comp);
                tileEntities.appendTag(comp);
            }
            compound.setTag("tileent", tileEntities);
        }

        if (mobileChunk.marker != null) {
            NBTTagCompound markerComp = new NBTTagCompound();
            markerComp.setInteger("markerPosX", mobileChunk.marker.coords.chunkPosX);
            markerComp.setInteger("markerPosY", mobileChunk.marker.coords.chunkPosY);
            markerComp.setInteger("markerPosZ", mobileChunk.marker.coords.chunkPosZ);
            compound.setTag("markerInfo", markerComp);
        }

        compound.setString("name", info.getName());
        if (info.getOwner() != null) {
            compound.setString("owner", info.getOwner().toString());
        }
        writeMovingWorldNBT(compound);
    }

    public abstract void writeMovingWorldNBT(NBTTagCompound compound);

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        byte[] ab = compound.getByteArray("chunk");
        ByteArrayInputStream bais = new ByteArrayInputStream(ab);
        DataInputStream in = new DataInputStream(bais);
        try {
            ChunkIO.read(in, mobileChunk);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (compound.hasKey("riderDestination")) {
            short s = compound.getShort("riderDestination");
            riderDestinationX = s & 0xF;
            riderDestinationY = s >>> 4 & 0xF;
            riderDestinationZ = s >>> 8 & 0xF;
            frontDirection = s >>> 12 & 3;
        } else {
            riderDestinationX = compound.getByte("riderDestinationX") & 0xFF;
            riderDestinationY = compound.getByte("riderDestinationY") & 0xFF;
            riderDestinationZ = compound.getByte("riderDestinationZ") & 0xFF;
            frontDirection = compound.getByte("front") & 3;
        }

        NBTTagList tileentities = compound.getTagList("tileent", 10);
        if (tileentities != null) {
            for (int i = 0; i < tileentities.tagCount(); i++) {
                NBTTagCompound comp = tileentities.getCompoundTagAt(i);
                TileEntity tileentity = TileEntity.createAndLoadEntity(comp);
                mobileChunk.setTileEntity(tileentity.xCoord, tileentity.yCoord, tileentity.zCoord, tileentity);
            }
        }

        if (compound.hasKey("markerInfo")) {
            NBTTagCompound markerComp = (NBTTagCompound) compound.getTag("markerInfo");
            int x = markerComp.getInteger("markerPosX");
            int y = markerComp.getInteger("markerPosY");
            int z = markerComp.getInteger("markerPosZ");

            mobileChunk.marker = new LocatedBlock(mobileChunk.getBlock(x, y, z), mobileChunk.getBlockMetadata(x, y, z), mobileChunk.getTileEntity(x, y, z), new ChunkPosition(x, y, z), null);
        }

        info = new MovingWorldInfo();
        info.setName(compound.getString("name"));
        if (compound.hasKey("owner")) {
            info.setOwner(UUID.fromString(compound.getString("owner")));
        }
        readMovingWorldNBT(compound);
    }

    public abstract void readMovingWorldNBT(NBTTagCompound compound);

    @SideOnly(Side.CLIENT)
    public void spawnParticles(double horvel) {
    }

    @Override
    public void writeSpawnData(ByteBuf data) {
        data.writeByte(riderDestinationX);
        data.writeByte(riderDestinationY);
        data.writeByte(riderDestinationZ);
        data.writeByte(frontDirection);

        data.writeShort(info.getName().length());
        data.writeBytes(info.getName().getBytes());

        try {
            ChunkIO.writeAllCompressed(data, mobileChunk);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MovingWorldSizeOverflowException ssoe) {
            disassemble(false);
            MovingWorld.logger.warn("Ship is too large to be sent");
        }
        writeMovingWorldSpawnData(data);
    }

    public abstract void writeMovingWorldSpawnData(ByteBuf data);

    @Override
    public void readSpawnData(ByteBuf data) {
        riderDestinationX = data.readUnsignedByte();
        riderDestinationY = data.readUnsignedByte();
        riderDestinationZ = data.readUnsignedByte();
        frontDirection = data.readUnsignedByte();

        byte[] ab = new byte[data.readShort()];
        data.readBytes(ab);
        info.setName(new String(ab));
        try {
            ChunkIO.readCompressed(data, mobileChunk);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mobileChunk.onChunkLoad();
        readMovingWorldSpawnData(data);
    }

    public abstract void handleControl(double horvel);

    public abstract void readMovingWorldSpawnData(ByteBuf data);

    public abstract float getXRenderScale();

    public abstract float getYRenderScale();

    public abstract float getZRenderScale();

    public abstract MovingWorldAssemblyInteractor getAssemblyInteractor();

    public abstract void setAssemblyInteractor(MovingWorldAssemblyInteractor interactor);

    public MobileChunk getMobileChunk() {
        return mobileChunk;
    }
}