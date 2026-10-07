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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. A crash: at rest, up to the safe speed (nothing), just past it (a little), at 1.0
 * (about 36%), at and past the wreck (all), monotone between, not finite (refused). A touchdown:
 * up to its safe sink (nothing), past it. What two moves lost: no change, a change the flight
 * could make (none), more. The closing speed of a collision: a wall met square (the whole speed,
 * met early or late in the tick), at a slant (the part into it), a roof (the climb), nothing lost
 * (none). Its cost: a floor met within the landing's safe sink (none), past it (a touchdown's), a
 * wall or a roof (a crash's), nothing lost (none).
 */
final class WearTest {

    @Test
    void aCrashCostsNothingUpToTheSafeSpeedThenGrowsWithTheSquareToAWreck() {
        assertEquals(0, Wear.crash(0.0));
        assertEquals(0, Wear.crash(Wear.SAFE));
        assertTrue(Wear.crash(Wear.SAFE + 0.01) > 0 && Wear.crash(Wear.SAFE + 0.01) < 100, "a little: " + Wear.crash(Wear.SAFE + 0.01));
        assertEquals(3600, Wear.crash(1.0), 1, "three quarters of the way to a wreck, squared");
        assertEquals(Wear.FULL_CONDITION, Wear.crash(Wear.WRECK));
        assertEquals(Wear.FULL_CONDITION, Wear.crash(9.0));
        int last = 0;
        for (double v = 0.0; v < 2.0; v += 0.01) {
            int w = Wear.crash(v);
            assertTrue(w >= last, "never less for a faster crash: " + v);
            last = w;
        }
        assertThrows(IllegalArgumentException.class, () -> Wear.crash(Double.NaN));
    }

    @Test
    void aLandingCostsNothingUpToItsSafeSinkAndAHardOneDoes() {
        assertEquals(0, Wear.touchdown(Flight.TOUCHDOWN));
        assertEquals(0, Wear.touchdown(Wear.TOUCHDOWN_SAFE));
        assertTrue(Wear.touchdown(0.6) > 0);
        assertTrue(Wear.touchdown(0.6) < Wear.crash(0.6), "the ground's safe sink is a little more than a wall's");
    }

    @Test
    void whatTwoMovesLostIsTheChangeTheFlightCouldNotHaveMade() {
        double shed = 0.04;
        assertEquals(0.0, Wear.lost(1, 0, 0, 1, 0, 0, shed), 1e-12, "no change");
        assertEquals(0.0, Wear.lost(1, 0, 0, 0.97, 0, 0, shed), 1e-12, "a change the flight could make");
        assertEquals(1.0 - shed, Wear.lost(1, 0, 0, 0, 0, 0, shed), 1e-12, "stopped");
        assertEquals(0.5 - shed, Wear.lost(0, -0.5, 0, 0, 0, 0, shed), 1e-12, "the ground met sinking");
    }

    @Test
    void theClosingSpeedIsTheSpeedCarriedAlongWhatWasLostWhereverInTheTickTheWallWasMet() {
        assertEquals(1.0, Wear.closing(1, 0, 0, 0, 0, 0), 1e-12, "a wall met square at the end of the tick");
        assertEquals(1.0, Wear.closing(1, 0, 0, 0.7, 0, 0), 1e-12, "the same wall met a third of the way in: the same crash");
        double v = 1.0 / Math.sqrt(2.0);
        assertEquals(v, Wear.closing(v, 0, v, 0, 0, v), 1e-12, "a wall met at a slant: only the part into it");
        assertEquals(0.4, Wear.closing(0, 0.4, 0, 0, 0.1, 0), 1e-12, "a roof met climbing");
        assertEquals(0.0, Wear.closing(0.6, 0, 0.2, 0.6, 0, 0.2), 1e-12, "nothing lost");
    }

    @Test
    void aCollisionCostsALandingGoingDownAndACrashOtherwise() {
        assertEquals(0, Wear.impact(0, -0.2, 0, 0, 0, 0), "a soft landing");
        assertEquals(Wear.touchdown(0.6), Wear.impact(0.1, -0.6, 0, 0.1, 0, 0), "a hard one, gliding a little");
        assertEquals(Wear.crash(0.6), Wear.impact(0, 0.6, 0, 0, 0, 0), "a roof met climbing is a crash");
        assertEquals(Wear.crash(1.0), Wear.impact(1.0, 0, 0, 0.2, 0, 0), "a wall");
        assertEquals(0, Wear.impact(0.5, 0, 0, 0.5, 0, 0), "nothing lost");
    }

    @Test
    void aLoadHeldByARoofIsChargedForWhatItCarriedNotWhatItsRopeAsked() {
        assertEquals(Wear.crash(0.4), Wear.impact(0, 0.4, 0, 0, 0.4, 0, 0, 0.1, 0), "rising at 0.4 into the roof: a crash at 0.4");
        assertEquals(0, Wear.impact(0, 0, 0, 0, 1.6, 0, 0, 0, 0), "held there, the rope asking more each tick, it carries nothing into it");
        assertEquals(Wear.crash(1.0), Wear.impact(1.0, 0, 0, 0.9, 0.3, 0, 0, 0.3, 0), "swung into a wall at 1.0, the rope pulling it up as well");
        assertEquals(0, Wear.impact(1.0, 0, 0, 0.9, 0.3, 0, 0.9, 0.3, 0), "swinging free, the rope turning it: nothing lost to the world");
    }
}
