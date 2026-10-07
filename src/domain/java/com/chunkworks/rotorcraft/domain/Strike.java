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
package com.chunkworks.rotorcraft.domain;

/**
 * What a spinning rotor does to what it touches (Rusty, 2026-10-07: "hit a bird in the blades? It
 * gets hurt, possibly killed. You jump into the spinning rotors? Same deal"). The blades sweep
 * their whole disc many times between two ticks, so the disc strikes: a slab
 * {@link #HALF_THICKNESS} either side of the blades' plane, out to their reach. How hard goes with
 * the rotor's speed.
 */
public final class Strike {
    private Strike() {}

    /** Half the disc's thickness, blocks: the blades' plane and a quarter of a block either side. */
    public static final double HALF_THICKNESS = 0.25;
    /** The damage of a strike at full rotor speed: five hearts. */
    public static final double FULL = 10.0;
    /** The least rotor speed (0..1) that strikes at all: a rotor barely turning pushes nothing aside. */
    public static final double LEAST_SPOOL = 0.2;

    /**
     * A rotor's disc in the world, blocks: its hub, the unit axis it turns about, its reach.
     *
     * <p>RI: (nx, ny, nz) has length 1 within 1e-6; radius >= 0; all finite.
     */
    public record Disc(double cx, double cy, double cz, double nx, double ny, double nz, double radius) {
        public Disc {
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(Math.abs(len - 1.0) <= 1e-6 && radius >= 0.0) || !Double.isFinite(cx + cy + cz + radius)) {
                throw new IllegalArgumentException("a hub, a unit axis and a reach: " + cx + "," + cy + "," + cz + " axis "
                        + nx + "," + ny + "," + nz + " reach " + radius);
            }
        }
    }

    /**
     * requires: x0 <= x1, y0 <= y1, z0 <= z1
     * effects: returns whether the box from (x0, y0, z0) to (x1, y1, z1) touches {@code d}: the
     * box's span along the axis meets the slab (exactly, by its corners), and the box's point
     * nearest the hub is within the reach of the axis. The reach is judged at that one point, so
     * against a tilted disc a box at the rim can be taken a little early; exact for a disc whose
     * axis is a world axis.
     */
    public static boolean touches(Disc d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            double a = ((i & 1) == 0 ? x0 : x1) - d.cx();
            double b = ((i & 2) == 0 ? y0 : y1) - d.cy();
            double c = ((i & 4) == 0 ? z0 : z1) - d.cz();
            double along = a * d.nx() + b * d.ny() + c * d.nz();
            lo = Math.min(lo, along);
            hi = Math.max(hi, along);
        }
        if (lo > HALF_THICKNESS || hi < -HALF_THICKNESS) {
            return false;
        }
        double qx = Math.clamp(d.cx(), x0, x1) - d.cx();
        double qy = Math.clamp(d.cy(), y0, y1) - d.cy();
        double qz = Math.clamp(d.cz(), z0, z1) - d.cz();
        double along = qx * d.nx() + qy * d.ny() + qz * d.nz();
        double rx = qx - along * d.nx(), ry = qy - along * d.ny(), rz = qz - along * d.nz();
        return rx * rx + ry * ry + rz * rz <= d.radius() * d.radius();
    }

    /** effects: returns the damage of a strike at rotor speed {@code spool} (0..1): none under LEAST_SPOOL, else FULL by the speed */
    public static double damage(double spool) {
        return spool < LEAST_SPOOL ? 0.0 : FULL * Math.min(1.0, spool);
    }
}
