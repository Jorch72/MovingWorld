package darkevilmac.movingworld.util;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Adds rotateRoll.
 */

public class Vec3Mod extends Vec3 {

    public static Vec3Mod getOrigin() {
        return new Vec3Mod(0, 0, 0);
    }

    public Vec3Mod(Vec3 vec3) {
        super(vec3.xCoord, vec3.yCoord, vec3.zCoord);
    }

    public Vec3Mod(double x, double y, double z) {
        super(x, y, z);
    }

    public Vec3Mod setX(double x) {
        return new Vec3Mod(x, this.yCoord, this.zCoord);
    }

    public Vec3Mod setY(double y) {
        return new Vec3Mod(this.xCoord, y, this.zCoord);
    }

    public Vec3Mod setZ(double z) {
        return new Vec3Mod(this.xCoord, this.yCoord, z);
    }

    /**
     * Rotate around the X axis
     *
     * @param pitch
     * @return
     */
    public Vec3Mod rotatePitch(float pitch) {
        return new Vec3Mod(super.rotatePitch(pitch));
    }

    /**
     * Rotate around the Y axis
     *
     * @param yaw
     * @return
     */
    public Vec3Mod rotateYaw(float yaw) {
        return new Vec3Mod(super.rotateYaw(yaw));
    }

    public Vec3Mod rotateAroundY(float p_72442_1_) {
        float var2 = MathHelper.cos(p_72442_1_);
        float var3 = MathHelper.sin(p_72442_1_);
        double var4 = this.xCoord * (double) var2 + this.zCoord * (double) var3;
        double var6 = this.yCoord;
        double var8 = this.zCoord * (double) var2 - this.xCoord * (double) var3;
        return new Vec3Mod(var4, var6, var8);
    }

    /**
     * Rotate around the Z axis.
     *
     * @param roll
     * @return
     */
    public Vec3Mod rotateRoll(float roll) {
        float var2 = MathHelper.cos(roll);
        float var3 = MathHelper.sin(roll);
        double var4 = this.xCoord * (double) var2 + this.yCoord * (double) var3;
        double var6 = this.yCoord * (double) var2 - this.xCoord * (double) var3;
        double var8 = this.zCoord;
        return new Vec3Mod(var4, var6, var8);
    }

}