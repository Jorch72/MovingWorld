package darkevilmac.movingworld.common.core;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.WorldInfo;

public class MovingWorldInfo extends WorldInfo {

    public IMovingWorld movingWorld;
    public WorldInfo parentWorldInfo;

    public MovingWorldInfo(WorldInfo parentWorldInfo) {
        this.parentWorldInfo = parentWorldInfo;
    }

    @Override
    public void updateTagCompound(NBTTagCompound nbt, NBTTagCompound playerNbt) {
        nbt.setLong("RandomSeed", this.parentWorldInfo.getSeed());
        nbt.setString("generatorName", this.parentWorldInfo.getTerrainType().getWorldTypeName());
        nbt.setInteger("generatorVersion", this.parentWorldInfo.getTerrainType().getGeneratorVersion());
        nbt.setString("generatorOptions", this.parentWorldInfo.getGeneratorOptions());
        nbt.setInteger("GameType", this.parentWorldInfo.getGameType().getID());
        nbt.setBoolean("MapFeatures", this.parentWorldInfo.isMapFeaturesEnabled());
        nbt.setInteger("SpawnX", this.parentWorldInfo.getSpawnX());
        nbt.setInteger("SpawnY", this.parentWorldInfo.getSpawnY());
        nbt.setInteger("SpawnZ", this.parentWorldInfo.getSpawnZ());
        nbt.setLong("Time", this.parentWorldInfo.getWorldTotalTime());
        nbt.setLong("DayTime", this.parentWorldInfo.getWorldTime());
        nbt.setLong("SizeOnDisk", this.parentWorldInfo.getSizeOnDisk());
        nbt.setLong("LastPlayed", MinecraftServer.getCurrentTimeMillis());
        nbt.setString("LevelName", this.parentWorldInfo.getWorldName());
        nbt.setInteger("version", this.parentWorldInfo.getSaveVersion());
        nbt.setInteger("clearWeatherTime", this.parentWorldInfo.getCleanWeatherTime());
        nbt.setInteger("rainTime", this.parentWorldInfo.getRainTime());
        nbt.setBoolean("raining", this.parentWorldInfo.isRaining());
        nbt.setInteger("thunderTime", this.parentWorldInfo.getThunderTime());
        nbt.setBoolean("thundering", this.parentWorldInfo.isThundering());
        nbt.setBoolean("hardcore", this.parentWorldInfo.isHardcoreModeEnabled());
        nbt.setBoolean("allowCommands", this.parentWorldInfo.areCommandsAllowed());
        nbt.setBoolean("initialized", this.parentWorldInfo.isInitialized());
        nbt.setDouble("BorderCenterX", this.parentWorldInfo.getBorderCenterX());
        nbt.setDouble("BorderCenterZ", this.parentWorldInfo.getBorderCenterZ());
        nbt.setDouble("BorderSize", this.parentWorldInfo.getBorderSize());
        nbt.setLong("BorderSizeLerpTime", this.parentWorldInfo.getBorderLerpTime());
        nbt.setDouble("BorderSafeZone", this.parentWorldInfo.getBorderSafeZone());
        nbt.setDouble("BorderDamagePerBlock", this.parentWorldInfo.getBorderDamagePerBlock());
        nbt.setDouble("BorderSizeLerpTarget", this.parentWorldInfo.getBorderSize());
        nbt.setDouble("BorderWarningBlocks", (double) this.parentWorldInfo.getBorderWarningDistance());
        nbt.setDouble("BorderWarningTime", (double) this.parentWorldInfo.getBorderWarningTime());

        if (movingWorld != null) {
            //Save MovingWorld stuff.
            nbt.setIntArray("CoreBlockPos", new int[]{movingWorld.coreBlock().getX(), movingWorld.coreBlock().getY(), movingWorld.coreBlock().getZ()});
            nbt.setIntArray("MinBlockPos", new int[]{movingWorld.min().getX(), movingWorld.min().getY(), movingWorld.min().getZ()});
            nbt.setIntArray("MaxBlockPos", new int[]{movingWorld.max().getX(), movingWorld.max().getY(), movingWorld.max().getZ()});
            nbt.setDouble("PosX", movingWorld.worldTranslation().xCoord);
            nbt.setDouble("PosY", movingWorld.worldTranslation().yCoord);
            nbt.setDouble("PosZ", movingWorld.worldTranslation().zCoord);
            nbt.setDouble("MotionX", movingWorld.motion().xCoord);
            nbt.setDouble("MotionY", movingWorld.motion().yCoord);
            nbt.setDouble("MotionZ", movingWorld.motion().zCoord);
            nbt.setFloat("RotationX", movingWorld.rotation().x);
            nbt.setFloat("RotationY", movingWorld.rotation().y);
            nbt.setFloat("RotationZ", movingWorld.rotation().z);
        }
        if (this.parentWorldInfo.getDifficulty() != null) {
            nbt.setByte("Difficulty", (byte) this.parentWorldInfo.getDifficulty().getDifficultyId());
        }

        nbt.setBoolean("DifficultyLocked", this.parentWorldInfo.isDifficultyLocked());
        nbt.setTag("GameRules", this.parentWorldInfo.getGameRulesInstance().writeToNBT());

        if (playerNbt != null) {
            nbt.setTag("Player", playerNbt);
        }
    }

}
