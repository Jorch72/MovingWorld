package darkevilmac.movingworld.entity;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import darkevilmac.movingworld.MovingWorld;
import darkevilmac.movingworld.chunk.*;
import darkevilmac.movingworld.util.AABBRotator;
import darkevilmac.movingworld.util.MathHelperMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
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
public abstract class EntityMovingWorld extends Entity implements IEntityAdditionalSpawnData {

    public float motionYaw;
    public int frontDirection;
    protected float groundFriction, horFriction, vertFriction;
    boolean isFlying;
    int[] layeredBlockVolumeCount;
    private MobileChunk shipChunk;
    private MovingWorldCapabilities capabilities;
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

    public EntityMovingWorld(World world, MovingWorldCapabilities capabilities) {
        super(world);
        info = new MovingWorldInfo();
        this.capabilities = capabilities;
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
                        double d0 = y + 1;

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

    @SideOnly(Side.CLIENT)
    private void initClient() {
        shipChunk = new MobileChunkClient(worldObj, this);
    }

    private void initCommon() {
        shipChunk = new MobileChunkServer(worldObj, this);
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(30, Byte.valueOf((byte) 0));
    }

    public MobileChunk getShipChunk() {
        return shipChunk;
    }

    public MovingWorldCapabilities getCapabilities() {
        return capabilities;
    }

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
    public void setDead() {
        super.setDead();
        shipChunk.onChunkUnload();
        capabilities.clear();
    }

    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();
        if (shipChunk.isModified) {
            shipChunk.isModified = false;
        }
    }

    public void setRotatedBoundingBox() {
        if (shipChunk == null) {
            float hw = width / 2F;
            boundingBox.setBounds(posX - hw, posY, posZ - hw, posX + hw, posY + height, posZ + hw);
        } else {
            boundingBox.setBounds(posX - shipChunk.getCenterX(), posY, posZ - shipChunk.getCenterZ(), posX + shipChunk.getCenterX(), posY + height, posZ + shipChunk.getCenterZ());
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
                noControl = true;
            }
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

    protected void handleServerUpdate(double horvel) {
        //START outer forces
        byte b0 = 5;
        int bpermeter = (int) (b0 * (boundingBox.maxY - boundingBox.minY));
        float waterVolume = 0F;
        AxisAlignedBB axisalignedbb = AxisAlignedBB.getBoundingBox(0D, 0D, 0D, 0D, 0D, 0D);
        int belowWater = 0;
        for (; belowWater < bpermeter; belowWater++) {
            double d1 = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * belowWater / bpermeter;
            double d2 = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * (belowWater + 1) / bpermeter;
            axisalignedbb.setBounds(boundingBox.minX, d1, boundingBox.minZ, boundingBox.maxX, d2, boundingBox.maxZ);

            if (!isAABBInLiquidNotFall(worldObj, axisalignedbb)) {
                break;
            }
        }
        if (belowWater > 0 && layeredBlockVolumeCount != null) {
            int k = belowWater / b0;
            for (int y = 0; y <= k && y < layeredBlockVolumeCount.length; y++) {
                if (y == k) {
                    waterVolume += layeredBlockVolumeCount[y] * (belowWater % b0) * 1F / b0;
                } else {
                    waterVolume += layeredBlockVolumeCount[y] * 1F;
                }
            }
        }

        if (onGround) {
            isFlying = false;
        }

        float gravity = 0.05F;
        if (waterVolume > 0F) {
            isFlying = false;
            float buoyancyforce = 1F * waterVolume * gravity; //F = rho * V * g (Archimedes' law)
            float mass = capabilities.getMass();
            motionY += buoyancyforce / mass;
        }
        if (!isFlying()) {
            motionY -= gravity;
        }
        //END outer forces

        //START limit motion
        double newhorvel = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double maxvel = capabilities.getSpeedLimit();
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
        rotationPitch = rotationPitch + (motionYaw * capabilities.getBankingMultiplier() - rotationPitch) * 0.15f;
        motionYaw *= 0.7F;
        //motionYaw = MathHelper.clamp_float(motionYaw, -BASE_TURN_SPEED * ShipMod.instance.modConfig.turnSpeed, BASE_TURN_SPEED * ShipMod.instance.modConfig.turnSpeed);
        rotationYaw += motionYaw;
        setRotatedBoundingBox();
        moveEntity(motionX, motionY, motionZ);
        posY = Math.min(posY, worldObj.getHeight());
        motionX *= horFriction;
        motionY *= vertFriction;
        motionZ *= horFriction;

        setRotation(rotationYaw, rotationPitch);
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

    private void handlePlayerControl() {
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
        return capabilities.canFly() && isFlying;
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
        return entity instanceof EntityMovingWorldAttachment || entity.ridingEntity instanceof EntityMovingWorldAttachment || entity instanceof EntityLiving ? null : entity.boundingBox;
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

        Vec3 vec = Vec3.createVectorHelper(-shipChunk.getCenterX(), -shipChunk.minY(), -shipChunk.getCenterZ());
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

        if (!disassembler.canDisassemble()) {
            return false;
        }

        AssembleResult result = disassembler.doDisassemble();

        return true;
    }

    public void dropAsItems() {
        TileEntity tileentity;
        Block block;
        for (int i = shipChunk.minX(); i < shipChunk.maxX(); i++) {
            for (int j = shipChunk.minY(); j < shipChunk.maxY(); j++) {
                for (int k = shipChunk.minZ(); k < shipChunk.maxZ(); k++) {
                    tileentity = shipChunk.getTileEntity(i, j, k);
                    if (tileentity instanceof IInventory) {
                        IInventory inv = (IInventory) tileentity;
                        for (int it = 0; it < inv.getSizeInventory(); it++) {
                            ItemStack is = inv.getStackInSlot(it);
                            if (is != null) {
                                entityDropItem(is, 0F);
                            }
                        }
                    }
                    block = shipChunk.getBlock(i, j, k);

                    if (block != Blocks.air) {
                        int meta = shipChunk.getBlockMetadata(i, j, k);
                        block.dropBlockAsItem(worldObj, MathHelper.floor_double(posX), MathHelper.floor_double(posY), MathHelper.floor_double(posZ), meta, 0);
                    }
                }
            }
        }
    }

    void fillAirBlocks(Set<ChunkPosition> set, int x, int y, int z) {
        if (x < shipChunk.minX() - 1 || x > shipChunk.maxX() || y < shipChunk.minY() - 1 || y > shipChunk.maxY() || z < shipChunk.minZ() - 1 || z > shipChunk.maxZ())
            return;
        ChunkPosition pos = new ChunkPosition(x, y, z);
        if (set.contains(pos)) return;

        set.add(pos);
        if (shipChunk.setBlockAsFilledAir(x, y, z)) {
            fillAirBlocks(set, x, y + 1, z);
            //fillAirBlocks(set, x, y - 1, z);
            fillAirBlocks(set, x - 1, y, z);
            fillAirBlocks(set, x, y, z - 1);
            fillAirBlocks(set, x + 1, y, z);
            fillAirBlocks(set, x, y, z + 1);
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(shipChunk.getMemoryUsage());
        DataOutputStream out = new DataOutputStream(baos);
        try {
            ChunkIO.writeAll(out, shipChunk);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        compound.setByteArray("chunk", baos.toByteArray());
        compound.setByte("front", (byte) frontDirection);

        if (!shipChunk.chunkTileEntityMap.isEmpty()) {
            NBTTagList tileentities = new NBTTagList();
            for (TileEntity tileentity : shipChunk.chunkTileEntityMap.values()) {
                NBTTagCompound comp = new NBTTagCompound();
                tileentity.writeToNBT(comp);
                tileentities.appendTag(comp);
            }
            compound.setTag("tileent", tileentities);
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
        byte[] ab = compound.getByteArray("chunk");
        ByteArrayInputStream bais = new ByteArrayInputStream(ab);
        DataInputStream in = new DataInputStream(bais);
        try {
            ChunkIO.read(in, shipChunk);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        frontDirection = compound.getByte("front") & 3;

        NBTTagList tileentities = compound.getTagList("tileent", 10);
        if (tileentities != null) {
            for (int i = 0; i < tileentities.tagCount(); i++) {
                NBTTagCompound comp = tileentities.getCompoundTagAt(i);
                TileEntity tileentity = TileEntity.createAndLoadEntity(comp);
                shipChunk.setTileEntity(tileentity.xCoord, tileentity.yCoord, tileentity.zCoord, tileentity);
            }
        }

        info = new MovingWorldInfo();
        info.setName(compound.getString("name"));
        if (compound.hasKey("owner")) {
            info.setOwner(UUID.fromString(compound.getString("owner")));
        }
        readMovingWorldNBT(compound);
    }

    public abstract void readMovingWorldNBT(NBTTagCompound compound);

    @Override
    public void writeSpawnData(ByteBuf data) {
        data.writeByte(frontDirection);

        data.writeShort(info.getName().length());
        data.writeBytes(info.getName().getBytes());

        try {
            ChunkIO.writeAllCompressed(data, shipChunk);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ShipSizeOverflowException ssoe) {
            disassemble(false);
            MovingWorld.logger.warn("Ship is too large to be sent");
        }
        writeMovingWorldSpawnData(data);
    }

    public abstract void writeMovingWorldSpawnData(ByteBuf data);

    @Override
    public void readSpawnData(ByteBuf data) {
        frontDirection = data.readUnsignedByte();

        byte[] ab = new byte[data.readShort()];
        data.readBytes(ab);
        info.setName(new String(ab));
        try {
            ChunkIO.readCompressed(data, shipChunk);
        } catch (IOException e) {
            e.printStackTrace();
        }

        shipChunk.onChunkLoad();
        readMovingWorldSpawnData(data);
    }

    public abstract void readMovingWorldSpawnData(ByteBuf data);

    public abstract float getXRenderScale();

    public abstract float getYRenderScale();

    public abstract float getZRenderScale();
}