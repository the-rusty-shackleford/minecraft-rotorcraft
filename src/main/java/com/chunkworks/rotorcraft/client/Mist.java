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
import com.chunkworks.rotorcraft.api.AircraftProfile;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** The crop sprayer's mist, on every client: a fine fall from along the boom while it sprays. */
public final class Mist {
    private Mist() {}

    /** How many drops a tick, along a boom of a block; a wider boom more. */
    private static final double PER_BLOCK = 0.6;

    /** effects: puts a tick's drops under {@code aircraft}'s boom */
    public static void spray(Aircraft aircraft, VehicleProfile p) {
        AircraftProfile ap = aircraft.aircraft();
        Minecraft mc = Minecraft.getInstance();
        if (ap == null || ap.sprayer().isEmpty() || mc.level == null || mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) {
            return;
        }
        AircraftProfile.Sprayer s = ap.sprayer().get();
        Vec3 boom = aircraft.position().add(aircraft.rotate(p.localBlocks(s.at())));
        double h = Math.toRadians(aircraft.getYRot());
        double rx = -Math.cos(h), rz = -Math.sin(h);
        Vec3 drift = new Vec3(aircraft.getX() - aircraft.xo, 0.0, aircraft.getZ() - aircraft.zo);
        RandomSource random = mc.level.random;
        int drops = Math.max(2, (int) Math.round(s.width() * PER_BLOCK));
        for (int i = 0; i < drops; i++) {
            double u = (random.nextDouble() - 0.5) * s.width();
            mc.level.addParticle(ParticleTypes.WHITE_ASH, boom.x + rx * u, boom.y - 0.1, boom.z + rz * u,
                    drift.x * 0.5, -0.12 - random.nextDouble() * 0.08, drift.z * 0.5);
        }
    }
}
