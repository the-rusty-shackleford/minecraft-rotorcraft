/*
 * Rotorcraft - a rotorcraft protocol, layered on Vanilla Wheels.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.rotorcraft.client;

import com.chunkworks.rotorcraft.Aircraft;
import com.chunkworks.rotorcraft.SlungLoad;
import com.chunkworks.rotorcraft.api.SlingProfile;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.client.VehicleRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a slung load as Vanilla Wheels draws a vehicle, and, while it hangs from a hook, its rope:
 * a dark ribbon from its lift eye to the hook, as the game draws a lead, straight when taut and
 * sagging by the slack when not.
 */
public final class SlungLoadRenderer extends VehicleRenderer {
    /** How many pieces the rope is drawn in. */
    private static final int SEGMENTS = 24;
    /** The rope's half width, blocks. */
    private static final float HALF = 0.04f;

    public SlungLoadRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Vehicle vehicle, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(vehicle, entityYaw, partialTick, poseStack, buffers, packedLight);
        if (!(vehicle instanceof SlungLoad load) || !(load.tower() instanceof Aircraft aircraft)) {
            return;
        }
        SlingProfile sp = load.sling();
        Vec3 eye = load.eyePoint();
        Vec3 hook = aircraft.hookPoint();
        if (sp == null || eye == null || hook == null) {
            return;
        }
        // Both ends where they are drawn this frame: each body's offset from its interpolated position.
        Vec3 loadAt = new Vec3(Mth.lerp(partialTick, load.xo, load.getX()), Mth.lerp(partialTick, load.yo, load.getY()), Mth.lerp(partialTick, load.zo, load.getZ()));
        Vec3 aircraftAt = new Vec3(Mth.lerp(partialTick, aircraft.xo, aircraft.getX()), Mth.lerp(partialTick, aircraft.yo, aircraft.getY()), Mth.lerp(partialTick, aircraft.zo, aircraft.getZ()));
        Vec3 from = eye.subtract(load.position());
        Vec3 to = hook.subtract(aircraft.position()).add(aircraftAt).subtract(loadAt);
        drawRope(poseStack, buffers.getBuffer(RenderType.leash()), from, to, sp.rope(), packedLight);
    }

    /** effects: draws the rope from {@code a} to {@code b} (relative to the pose's origin), sagging by its slack below the line */
    private static void drawRope(PoseStack poseStack, VertexConsumer out, Vec3 a, Vec3 b, double rope, int light) {
        Matrix4f pose = poseStack.last().pose();
        double length = a.distanceTo(b);
        double sag = Math.max(0.0, rope - length) * 0.5;
        double dx = b.x - a.x, dz = b.z - a.z;
        double flat = Math.hypot(dx, dz);
        // Two ribbons, one across the line seen from above and one upright, so the rope has a body from any side.
        float sx = flat < 1e-4 ? HALF : (float) (-dz / flat * HALF), sz = flat < 1e-4 ? 0.0f : (float) (dx / flat * HALF);
        ribbon(out, pose, a, b, sag, sx, 0.0f, sz, light);
        ribbon(out, pose, a, b, sag, 0.0f, HALF, 0.0f, light);
    }

    private static void ribbon(VertexConsumer out, Matrix4f pose, Vec3 a, Vec3 b, double sag, float wx, float wy, float wz, int light) {
        for (int i = 0; i <= SEGMENTS; i++) {
            float f = (float) i / SEGMENTS;
            double x = Mth.lerp(f, a.x, b.x), z = Mth.lerp(f, a.z, b.z);
            double y = Mth.lerp(f, a.y, b.y) - sag * 4.0 * f * (1.0 - f);
            float shade = i % 2 == 0 ? 0.16f : 0.12f;
            out.addVertex(pose, (float) x - wx, (float) y - wy, (float) z - wz).setColor(shade, shade * 0.95f, shade * 0.9f, 1.0f).setLight(light);
            out.addVertex(pose, (float) x + wx, (float) y + wy, (float) z + wz).setColor(shade, shade * 0.95f, shade * 0.9f, 1.0f).setLight(light);
        }
    }
}
