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
 * A slung load's lift eye on its rope, hung from a hook: a point that falls, slows a little in the
 * air, and is held by the rope -- never farther from the hook than the rope is long -- so it swings
 * under a moving hook and settles straight under a still one. Position-based: each tick the eye
 * falls freely, is put back on the rope's sphere if the fall took it past, and its velocity is
 * what it actually moved. A slack rope holds nothing. Blocks and ticks, the world's frame.
 *
 * <p>RI: every component finite.
 * AF: AF(x, y, z, vx, vy, vz) = "the eye at (x, y, z), moving (vx, vy, vz) blocks a tick".
 */
public record Sling(double x, double y, double z, double vx, double vy, double vz) {

    /** What gravity adds to the fall each tick, blocks a tick: a mob's. */
    public static final double GRAVITY = 0.08;
    /** The share of its velocity the air takes each tick. */
    public static final double DRAG = 0.02;
    /** How far past the rope's length the world may hold the eye before the hook lets go: a snag. */
    public static final double SNAG = 1.5;
    /** How far to the side of the hook, and below its rope, a load's eye may be for the hook to take it. */
    public static final double REACH = 1.5;

    public Sling {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)) {
            throw new IllegalArgumentException("a sling is finite");
        }
    }

    /** effects: returns an eye at rest at (x, y, z) */
    public static Sling at(double x, double y, double z) {
        return new Sling(x, y, z, 0, 0, 0);
    }

    /**
     * requires: rope > 0, the hook finite
     * effects: returns the eye one tick on: velocity plus gravity, less the drag; moved by it; if
     * that leaves it farther than {@code rope} from the hook at (hx, hy, hz), put back on the
     * rope's sphere along the line from the hook; the velocity is then the move it made
     */
    public Sling step(double hx, double hy, double hz, double rope) {
        if (!(rope > 0)) {
            throw new IllegalArgumentException("a rope has a length: " + rope);
        }
        double nvx = vx * (1.0 - DRAG), nvy = (vy - GRAVITY) * (1.0 - DRAG), nvz = vz * (1.0 - DRAG);
        double px = x + nvx, py = y + nvy, pz = z + nvz;
        double dx = px - hx, dy = py - hy, dz = pz - hz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > rope) {
            double k = rope / d;
            px = hx + dx * k;
            py = hy + dy * k;
            pz = hz + dz * k;
        }
        return new Sling(px, py, pz, px - x, py - y, pz - z);
    }

    /**
     * effects: returns the eye at (nx, ny, nz), where the world let it go, having been stepped to
     * move (wx, wy, wz): its velocity loses its part along the direction the move lost and keeps the
     * rest ({@link Flight#struck}), so a load stopped by a wall or the ground does not drive into it
     * again
     */
    public static Sling struck(double nx, double ny, double nz, double wx, double wy, double wz, double gx, double gy, double gz) {
        double[] v = Flight.kept(wx, wy, wz, gx, gy, gz);
        return new Sling(nx, ny, nz, v[0], v[1], v[2]);
    }

    /** effects: returns how far the eye is from the hook at (hx, hy, hz) */
    public double from(double hx, double hy, double hz) {
        double dx = x - hx, dy = y - hy, dz = z - hz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** effects: returns whether the world holds the eye so far from the hook, past its {@code rope}, that the hook lets go */
    public static boolean snagged(double distance, double rope) {
        return distance > rope + SNAG;
    }

    /**
     * effects: returns whether a hook at (hx, hy, hz) can take a load whose eye is at (ex, ey, ez)
     * on a rope of {@code rope}: the eye within {@link #REACH} of straight below the hook, and
     * below it by no more than the rope and {@link #REACH}
     */
    public static boolean inReach(double hx, double hy, double hz, double ex, double ey, double ez, double rope) {
        double below = hy - ey;
        return Math.hypot(ex - hx, ez - hz) <= REACH && below >= 0.0 && below <= rope + REACH;
    }
}
