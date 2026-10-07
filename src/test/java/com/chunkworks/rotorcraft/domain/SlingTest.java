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
 * Partitions. A still hook: an eye to its side swings and settles straight under it at the
 * rope's length; the swing dies away. A moving hook: the eye follows on a taut rope, trailing. Any
 * step: never farther than the rope. A slack rope: the eye falls freely. Struck by the ground: the
 * fall gone, the drag kept. Snagged: past the rope by
 * more than {@link Sling#SNAG} (yes), by no more (no). In reach: straight below within the rope
 * (yes), at the side's limit (yes), past it (no), above the hook (no), past the rope's reach (no).
 * A rope of no length is refused.
 */
final class SlingTest {
    private static final double ROPE = 4.0;

    @Test
    void underAStillHookItSettlesStraightBelowAtTheRopesLength() {
        Sling s = Sling.at(3.0, 0.0, 0.0);   // to the side of a hook at (0, 4, 0)
        double firstSwing = 0.0;
        for (int i = 0; i < 40; i++) {
            s = s.step(0, ROPE, 0, ROPE);
            firstSwing = Math.max(firstSwing, Math.abs(s.x()));
        }
        for (int i = 0; i < 1000; i++) {
            s = s.step(0, ROPE, 0, ROPE);
        }
        assertEquals(0.0, s.x(), 0.01, "straight under the hook");
        assertEquals(0.0, s.y(), 0.01, "the rope's length below it");
        assertTrue(firstSwing > 1.0, "it swung on the way: " + firstSwing);
    }

    @Test
    void itFollowsAMovingHookOnATautRopeAndIsNeverFartherThanTheRope() {
        Sling s = Sling.at(0.0, 0.0, 0.0);
        double hx = 0.0;
        for (int i = 0; i < 400; i++) {
            hx += 1.0;   // the hook flies east at a block a tick, four blocks up
            s = s.step(hx, ROPE, 0, ROPE);
            assertTrue(s.from(hx, ROPE, 0) <= ROPE + 1e-9, "never past the rope: tick " + i);
        }
        assertEquals(ROPE, s.from(hx, ROPE, 0), 1e-6, "taut");
        assertTrue(s.x() < hx, "trailing behind the hook");
        assertEquals(1.0, s.vx(), 0.01, "keeping its speed");
    }

    @Test
    void onASlackRopeItFallsFreely() {
        Sling s = Sling.at(0.0, 0.0, 0.0);
        Sling next = s.step(0, 10.0, 0, ROPE * 10);
        assertEquals(-Sling.GRAVITY * (1.0 - Sling.DRAG), next.vy(), 1e-12, "gravity alone, less the drag");
        assertThrows(IllegalArgumentException.class, () -> s.step(0, 1, 0, 0.0), "a rope of no length");
    }

    @Test
    void struckOnTheGroundItKeepsItsSlideButNotItsFall() {
        Sling s = Sling.struck(1, 2, 3, 0.3, -0.5, 0.0, 0.3, -0.1, 0.0);
        assertEquals(0.0, s.vy(), 1e-12, "the fall into the ground gone");
        assertEquals(0.3, s.vx(), 1e-12, "the drag along it kept");
        assertEquals(1.0, s.x(), 0.0, "where the world put it");
    }

    @Test
    void snaggedIsPastTheRopeByMoreThanTheSnagAndReachIsBelowTheHook() {
        assertFalse(Sling.snagged(ROPE + Sling.SNAG, ROPE));
        assertTrue(Sling.snagged(ROPE + Sling.SNAG + 0.01, ROPE));
        assertTrue(Sling.inReach(0, 10, 0, 0, 8, 0, ROPE), "straight below within the rope");
        assertTrue(Sling.inReach(0, 10, 0, Sling.REACH, 8, 0, ROPE), "at the side's limit");
        assertFalse(Sling.inReach(0, 10, 0, Sling.REACH + 0.01, 8, 0, ROPE), "past it");
        assertFalse(Sling.inReach(0, 10, 0, 0, 10.5, 0, ROPE), "above the hook");
        assertTrue(Sling.inReach(0, 10, 0, 0, 10 - ROPE - Sling.REACH, 0, ROPE), "at the rope's reach");
        assertFalse(Sling.inReach(0, 10, 0, 0, 10 - ROPE - Sling.REACH - 0.01, 0, ROPE), "past the rope's reach");
    }
}
