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

import com.chunkworks.vanillawheels.domain.Hull;
import java.util.List;

/**
 * Where a rider steps out of an aircraft: beside their own seat, at the seat's place along the
 * body, just clear of its side -- the door. Blocks in the body's frame, as {@link Hull}'s boxes
 * are: x across (+x the left), z along (+z forward). How far down the ground is, the world says.
 */
public final class Exit {
    private Exit() {}

    /** The gap left between the body's side and the rider, blocks. */
    public static final double GAP = 0.25;

    /** A door: x across, z along, blocks in the body's frame. */
    public record Spot(double x, double z) {}

    /**
     * requires: bodyHalf >= 0, riderHalf >= 0, all finite
     * effects: returns the two doors for a rider of half-width {@code riderHalf} sitting at
     * ({@code seatX}, {@code seatZ}), best first: the seat's own side, then the other; a seat on
     * the centre line takes the left first. Each is at the seat's z, {@link #GAP} clear of that
     * side of the body: of {@code bodyHalf}, and of the farthest any box of {@code hull} reaches
     * on that side where it spans the rider's place along the body.
     * throws: IllegalArgumentException if a half-width is negative or anything is not finite
     */
    public static List<Spot> doors(double seatX, double seatZ, List<Hull.Box> hull, double bodyHalf, double riderHalf) {
        if (!(bodyHalf >= 0 && riderHalf >= 0) || !Double.isFinite(seatX + seatZ + bodyHalf + riderHalf)) {
            throw new IllegalArgumentException("a seat and two half-widths: " + seatX + ", " + seatZ + ", " + bodyHalf + ", " + riderHalf);
        }
        double left = bodyHalf;
        double right = bodyHalf;
        for (Hull.Box b : hull) {
            if (b.z0() < seatZ + riderHalf && b.z1() > seatZ - riderHalf) {
                left = Math.max(left, b.x1());
                right = Math.max(right, -b.x0());
            }
        }
        Spot l = new Spot(left + riderHalf + GAP, seatZ);
        Spot r = new Spot(-(right + riderHalf + GAP), seatZ);
        return seatX >= 0 ? List.of(l, r) : List.of(r, l);
    }
}
