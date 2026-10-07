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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions, on the Huey's discs. Against the slab: a box through the blades' plane; one over it
 * within the half-thickness, and beyond it; one under it beyond it. Against the reach: within it;
 * beyond it; the hub inside the box. The axis: upright (the main rotor), level (the tail rotor:
 * a player standing under it clears it by a hair, one jumping is struck), tilted twelve degrees (a
 * bird under the dipped front edge, which the level disc would miss). Damage: under the least
 * speed, at it, half, full. Refused: an axis not of unit length, a negative reach.
 */
final class StrikeTest {
    /** The Huey's main rotor, level: hub 3.9 over the skids, reach 7.36. */
    private static final Strike.Disc MAIN = new Strike.Disc(0.0, 3.9, 0.0, 0.0, 1.0, 0.0, 7.36);
    /** Its tail rotor: hub 3.11 up and 8.85 back, turning about the body's cross axis, reach 1.29. */
    private static final Strike.Disc TAIL = new Strike.Disc(0.33, 3.11, -8.85, 1.0, 0.0, 0.0, 1.29);

    /** effects: whether a box of width w and height h standing at (x, y, z) touches d */
    private static boolean standing(Strike.Disc d, double x, double y, double z, double w, double h) {
        return Strike.touches(d, x - w / 2, y, z - w / 2, x + w / 2, y + h, z + w / 2);
    }

    @Test
    void aBoxThroughTheBladesPlaneWithinReachIsStruck() {
        assertTrue(standing(MAIN, 5.0, 3.5, 2.0, 0.4, 0.7), "a chicken's box through the plane, 5.4 out");
    }

    @Test
    void aBoxOverThePlaneIsStruckWithinTheHalfThicknessOnly() {
        assertTrue(standing(MAIN, 3.0, 3.9 + Strike.HALF_THICKNESS - 0.01, 0.0, 0.6, 1.8), "its feet a hair inside the slab");
        assertFalse(standing(MAIN, 3.0, 3.9 + Strike.HALF_THICKNESS + 0.01, 0.0, 0.6, 1.8), "its feet a hair over it");
    }

    @Test
    void aBoxUnderTheSlabIsNotStruck() {
        // A player standing on the ground beside the cabin, even jumping: the head at 3.05.
        assertFalse(standing(MAIN, 2.5, 1.25, 0.0, 0.6, 1.8));
    }

    @Test
    void aBoxBeyondTheReachIsNotStruckButOneJustInsideIs() {
        assertFalse(standing(MAIN, 7.36 + 0.31, 3.5, 0.0, 0.6, 0.7), "its near face 0.01 past the tips");
        assertTrue(standing(MAIN, 7.36 + 0.29, 3.5, 0.0, 0.6, 0.7), "its near face 0.01 inside them");
    }

    @Test
    void aBoxRoundTheHubIsStruck() {
        assertTrue(standing(MAIN, 0.0, 3.0, 0.0, 0.6, 1.8));
    }

    @Test
    void underTheTailRotorAStandingPlayerClearsItAndAJumpingOneIsStruck() {
        assertFalse(standing(TAIL, 0.33, 0.0, -8.85, 0.6, 1.8), "the disc's bottom at 1.82, the head at 1.8");
        assertTrue(standing(TAIL, 0.33, 0.5, -8.85, 0.6, 1.8), "mid-jump, the head at 2.3");
        assertFalse(standing(TAIL, 0.33 + 0.6, 1.5, -8.85, 0.6, 1.8), "beside it, clear of its plane");
    }

    @Test
    void aTiltedDiscStrikesUnderItsDippedEdgeWhereALevelOneWouldNot() {
        double t = Math.toRadians(12.0);
        Strike.Disc tilted = new Strike.Disc(0.0, 3.9, 0.0, 0.0, Math.cos(t), Math.sin(t), 7.36);
        // Six forward the tilted blades pass at 3.9 - 6 tan 12 = 2.62; a bird's box from 2.3 to 3.0 there.
        assertTrue(standing(tilted, 0.0, 2.3, 6.0, 0.4, 0.7));
        assertFalse(standing(MAIN, 0.0, 2.3, 6.0, 0.4, 0.7));
    }

    @Test
    void theDamageGoesWithTheRotorsSpeedAndNoneUnderTheLeast() {
        assertEquals(0.0, Strike.damage(0.0));
        assertEquals(0.0, Strike.damage(Strike.LEAST_SPOOL - 0.01));
        assertEquals(Strike.FULL * Strike.LEAST_SPOOL, Strike.damage(Strike.LEAST_SPOOL), 1e-9);
        assertEquals(Strike.FULL / 2.0, Strike.damage(0.5), 1e-9);
        assertEquals(Strike.FULL, Strike.damage(1.0), 1e-9);
    }

    @Test
    void anAxisNotOfUnitLengthOrANegativeReachIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Strike.Disc(0, 0, 0, 0, 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Strike.Disc(0, 0, 0, 0, 1, 0, -1));
    }
}
