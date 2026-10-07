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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Points: no boxes; a box smaller than the spacing (its eight corners); a long box
 * (every corner, no gap over the spacing, a full block anywhere inside it holds a point); two boxes
 * (their counts add). Reach: boxes only (the farthest corner, the highest top); a disc beyond them;
 * nothing. Boxes given backwards are put the right way round; a backwards box is refused.
 */
final class HullTest {

    @Test
    void noBoxesHaveNoPoints() {
        assertEquals(0, Hull.points(List.of()).length);
    }

    @Test
    void aBoxSmallerThanTheSpacingIsItsEightCorners() {
        double[] p = Hull.points(List.of(new Hull.Box(0, 0, 0, 0.5, 0.3, 0.8)));
        assertEquals(8 * 3, p.length);
        for (int i = 0; i < p.length; i += 3) {
            assertTrue(p[i] == 0 || p[i] == 0.5, "x on a face: " + p[i]);
            assertTrue(p[i + 1] == 0 || p[i + 1] == 0.3, "y on a face: " + p[i + 1]);
            assertTrue(p[i + 2] == 0 || p[i + 2] == 0.8, "z on a face: " + p[i + 2]);
        }
    }

    @Test
    void aLongBoxLeavesNoGapABlockCouldStandIn() {
        Hull.Box cabin = new Hull.Box(-1.2, 0.44, -2.6, 1.2, 2.28, 3.6);
        double[] p = Hull.points(List.of(cabin));
        assertEquals(3 * Hull.samples(2.4) * Hull.samples(1.84) * Hull.samples(6.2), p.length);
        assertEquals(4 * 4 * 8, p.length / 3, "2.4 by 1.84 by 6.2 at 0.9: four, four and eight points");
        // Every corner is a point.
        for (double x : new double[] {-1.2, 1.2}) {
            for (double y : new double[] {0.44, 2.28}) {
                for (double z : new double[] {-2.6, 3.6}) {
                    assertTrue(holds(p, x, y, z, 1e-9), "corner " + x + "," + y + "," + z);
                }
            }
        }
        // A full block anywhere inside the box, in steps of a tenth, holds a point.
        for (double bx = -1.2; bx <= 0.2; bx += 0.1) {
            for (double by = 0.44; by <= 1.28; by += 0.1) {
                for (double bz = -2.6; bz <= 2.6; bz += 0.1) {
                    assertTrue(inBlock(p, bx, by, bz), "a block at " + bx + "," + by + "," + bz + " holds no point");
                }
            }
        }
    }

    @Test
    void twoBoxesTakeBothTheirPoints() {
        Hull.Box a = new Hull.Box(0, 0, 0, 1, 1, 1);
        Hull.Box b = new Hull.Box(0, 0, 2, 0.4, 0.4, 7);
        assertEquals(Hull.points(List.of(a)).length + Hull.points(List.of(b)).length, Hull.points(List.of(a, b)).length);
    }

    @Test
    void boxesAreGivenEitherWayRoundButNotHeldBackwards() {
        assertEquals(new Hull.Box(-1, 0, -3, 1, 2, 4), Hull.Box.spanning(1, 2, 4, -1, 0, -3));
        assertThrows(IllegalArgumentException.class, () -> new Hull.Box(1, 0, 0, -1, 1, 1));
    }

    @Test
    void reachIsTheFarthestCornerOrDiscEdgeAndTheHighestTop() {
        Hull.Box boom = new Hull.Box(-0.5, 0.8, -9.0, 0.5, 1.9, -2.6);
        double[] boxes = Hull.reach(List.of(boom), List.of());
        assertEquals(Math.hypot(0.5, 9.0), boxes[0], 1e-12, "the boom's far corner");
        assertEquals(1.9, boxes[1], 1e-12, "the boom's top");
        double[] withRotor = Hull.reach(List.of(boom), List.of(new Hull.Disc(0, 3.9, 0, 7.355), new Hull.Disc(0.33, 3.11, -8.85, 1.295)));
        assertEquals(Math.hypot(0.33, 8.85) + 1.295, withRotor[0], 1e-12, "the tail rotor's disc reaches farthest");
        assertEquals(3.9 + 7.355, withRotor[1], 1e-12, "a disc's top is its centre plus its radius, whichever way it turns");
        assertArrayEquals(new double[] {0, 0}, Hull.reach(List.of(), List.of()), 0.0);
    }

    /** effects: returns whether a point of p is within tol of (x, y, z) */
    private static boolean holds(double[] p, double x, double y, double z, double tol) {
        for (int i = 0; i < p.length; i += 3) {
            if (Math.abs(p[i] - x) <= tol && Math.abs(p[i + 1] - y) <= tol && Math.abs(p[i + 2] - z) <= tol) {
                return true;
            }
        }
        return false;
    }

    /** effects: returns whether a point of p lies in the unit block whose least corner is (x, y, z) */
    private static boolean inBlock(double[] p, double x, double y, double z) {
        for (int i = 0; i < p.length; i += 3) {
            if (p[i] >= x && p[i] <= x + 1 && p[i + 1] >= y && p[i + 1] <= y + 1 && p[i + 2] >= z && p[i + 2] <= z + 1) {
                return true;
            }
        }
        return false;
    }
}
