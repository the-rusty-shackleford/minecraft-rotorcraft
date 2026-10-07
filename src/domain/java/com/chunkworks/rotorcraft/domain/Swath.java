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
 * Which block columns a spray boom wets in a tick: everything the boom's line swept between where
 * it was and where it is, so a pass at speed leaves no dry stripe between two ticks' lines (at 1.4
 * blocks a tick the boom moves past a whole column each tick). The boom is a line {@code width}
 * blocks long, centred on its point, square to the heading. Columns are reported as the block
 * coordinates that hold a sample, the swept area sampled at most {@link #STEP} apart both ways; a
 * column may be reported more than once in a sweep, and a caller doses each once (a column dosed
 * stays dosed for a cooldown: {@link #due}).
 */
public final class Swath {
    private Swath() {}

    /** The spacing of the samples, blocks: finer than a column, so none is skipped. */
    public static final double STEP = 0.5;

    /** Where the sweep reports a column. */
    @FunctionalInterface
    public interface Columns {
        void column(int x, int z);
    }

    /**
     * requires: width > 0; every coordinate and heading finite
     * effects: reports to {@code out} every column a boom {@code width} long swept going from its
     * centre at (x0, z0) facing {@code h0} to (x1, z1) facing {@code h1} (the game's yaw), samples
     * at most {@link #STEP} apart along the travel and across the boom, both lines included
     */
    public static void sweep(double x0, double z0, double h0, double x1, double z1, double h1, double width, Columns out) {
        if (!(width > 0)) {
            throw new IllegalArgumentException("a boom has a width: " + width);
        }
        double travel = Math.hypot(x1 - x0, z1 - z0);
        int along = Math.max(1, (int) Math.ceil(travel / STEP));
        int across = Math.max(1, (int) Math.ceil(width / STEP));
        for (int i = 0; i <= along; i++) {
            double t = (double) i / along;
            double cx = x0 + (x1 - x0) * t, cz = z0 + (z1 - z0) * t;
            double h = h0 + Flight.wrap(h1 - h0) * t;
            // The boom lies along the body's right, (-cos h, -sin h).
            double rx = -Math.cos(h), rz = -Math.sin(h);
            for (int j = 0; j <= across; j++) {
                double u = ((double) j / across - 0.5) * width;
                out.column((int) Math.floor(cx + rx * u), (int) Math.floor(cz + rz * u));
            }
        }
    }

    /** effects: returns whether a column last dosed at tick {@code last} is due again at {@code now}, a dose lasting {@code cooldown} ticks */
    public static boolean due(long last, long now, int cooldown) {
        return now - last >= cooldown;
    }
}
