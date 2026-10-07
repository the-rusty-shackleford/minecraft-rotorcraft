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

import java.util.List;

/**
 * What an aircraft's body is made of for its collisions in the air: boxes, in blocks in the body's
 * frame (x across, y up, z along), each sampled at points no more than {@link #SPACING} apart over
 * its whole volume -- every corner among them -- so no full block can stand inside a box without
 * holding a point. A long, thin aircraft is several boxes (a cabin, a boom, a fin), where two
 * points at the ends of one long box would miss the boom's whole middle.
 */
public final class Hull {
    private Hull() {}

    /** The most two neighbouring points of a box are apart along any axis, blocks: under a block. */
    public static final double SPACING = 0.9;

    /**
     * A box, blocks in the body's frame.
     *
     * <p>RI: x0 <= x1, y0 <= y1, z0 <= z1; all finite.
     */
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (!(x0 <= x1 && y0 <= y1 && z0 <= z1) || !Double.isFinite(x0 + y0 + z0 + x1 + y1 + z1)) {
                throw new IllegalArgumentException("a box from its least corner to its greatest: " + x0 + "," + y0 + "," + z0 + " to " + x1 + "," + y1 + "," + z1);
            }
        }

        /** effects: returns the box with corners a and b, whichever way round they are given */
        public static Box spanning(double ax, double ay, double az, double bx, double by, double bz) {
            return new Box(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
        }
    }

    /** effects: returns how many points an extent of {@code length} blocks is sampled at: both ends, and none more than SPACING apart */
    static int samples(double length) {
        return Math.max(2, (int) Math.ceil(length / SPACING - 1e-9) + 1);
    }

    /**
     * effects: returns the points the boxes are probed at, as x, y, z triples in one array: each
     * box's grid, both ends of every axis included, the points evenly spaced and no more than
     * SPACING apart; empty for no boxes
     */
    public static double[] points(List<Box> boxes) {
        int n = 0;
        for (Box b : boxes) {
            n += samples(b.x1() - b.x0()) * samples(b.y1() - b.y0()) * samples(b.z1() - b.z0());
        }
        double[] out = new double[3 * n];
        int k = 0;
        for (Box b : boxes) {
            int nx = samples(b.x1() - b.x0()), ny = samples(b.y1() - b.y0()), nz = samples(b.z1() - b.z0());
            for (int i = 0; i < nx; i++) {
                double x = b.x0() + (b.x1() - b.x0()) * i / (nx - 1);
                for (int j = 0; j < ny; j++) {
                    double y = b.y0() + (b.y1() - b.y0()) * j / (ny - 1);
                    for (int l = 0; l < nz; l++) {
                        out[k++] = x;
                        out[k++] = y;
                        out[k++] = b.z0() + (b.z1() - b.z0()) * l / (nz - 1);
                    }
                }
            }
        }
        return out;
    }

    /**
     * A disc that turns, for how far the aircraft reaches: its centre in blocks in the body's frame,
     * and its radius.
     */
    public record Disc(double x, double y, double z, double radius) {}

    /**
     * effects: returns {reach, top}: how far from the body's origin, across the ground, anything of
     * the boxes and the discs reaches (the farthest corner, or a disc's centre plus its radius,
     * whichever way the body is turned), and how high above it anything reaches; {0, 0} for none
     */
    public static double[] reach(List<Box> boxes, List<Disc> discs) {
        double reach = 0.0, top = 0.0;
        for (Box b : boxes) {
            for (double x : new double[] {b.x0(), b.x1()}) {
                for (double z : new double[] {b.z0(), b.z1()}) {
                    reach = Math.max(reach, Math.hypot(x, z));
                }
            }
            top = Math.max(top, b.y1());
        }
        for (Disc d : discs) {
            reach = Math.max(reach, Math.hypot(d.x(), d.z()) + d.radius());
            top = Math.max(top, d.y() + d.radius());
        }
        return new double[] {reach, top};
    }
}
