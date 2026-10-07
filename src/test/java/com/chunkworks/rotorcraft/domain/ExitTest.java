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

import com.chunkworks.vanillawheels.domain.Hull;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. The seat's side: left (x > 0), right (x < 0), the centre line (x = 0). The hull:
 * none (the body's half-width rules); a box spanning the seat wider than the body (the box rules);
 * one narrower (the body rules); one beside the seat along the body, not spanning it (ignored),
 * just touching the rider's span (ignored) or just overlapping it (counted); one reaching farther
 * on one side than the other (each side its own). Refused: a negative half-width, a NaN.
 */
final class ExitTest {
    private static final double RIDER = 0.3;
    private static final double E = 1e-9;

    private static void assertSpot(double x, double z, Exit.Spot s) {
        assertEquals(x, s.x(), E, "x");
        assertEquals(z, s.z(), E, "z");
    }

    @Test
    void aLeftSeatGoesOutOnTheLeftFirstThenTheRight() {
        List<Exit.Spot> d = Exit.doors(0.55, 1.75, List.of(), 1.3, RIDER);
        assertSpot(1.3 + RIDER + Exit.GAP, 1.75, d.get(0));
        assertSpot(-(1.3 + RIDER + Exit.GAP), 1.75, d.get(1));
    }

    @Test
    void aRightSeatGoesOutOnTheRightFirst() {
        List<Exit.Spot> d = Exit.doors(-0.55, 1.75, List.of(), 1.3, RIDER);
        assertSpot(-(1.3 + RIDER + Exit.GAP), 1.75, d.get(0));
        assertSpot(1.3 + RIDER + Exit.GAP, 1.75, d.get(1));
    }

    @Test
    void aSeatOnTheCentreLineTakesTheLeftFirst() {
        List<Exit.Spot> d = Exit.doors(0.0, -0.6, List.of(), 1.3, RIDER);
        assertEquals(2, d.size());
        assertSpot(1.3 + RIDER + Exit.GAP, -0.6, d.get(0));
    }

    @Test
    void aBoxWiderThanTheBodyAtTheSeatKeepsTheDoorClearOfIt() {
        // The Huey's skids, 1.36 out, under a body 1.3 to a side.
        Hull.Box skids = new Hull.Box(-1.36, 0.0, -1.55, 1.36, 0.44, 2.72);
        List<Exit.Spot> d = Exit.doors(0.55, 1.75, List.of(skids), 1.3, RIDER);
        assertSpot(1.36 + RIDER + Exit.GAP, 1.75, d.get(0));
        assertSpot(-(1.36 + RIDER + Exit.GAP), 1.75, d.get(1));
    }

    @Test
    void aBoxNarrowerThanTheBodyLeavesTheBodysWidth() {
        Hull.Box cabin = new Hull.Box(-1.2, 0.44, -2.63, 1.2, 2.28, 3.57);
        assertSpot(1.3 + RIDER + Exit.GAP, 1.75, Exit.doors(0.55, 1.75, List.of(cabin), 1.3, RIDER).get(0));
    }

    @Test
    void aBoxElsewhereAlongTheBodyIsNotAtTheDoor() {
        // The tail's stabiliser, 1.42 out, five blocks behind the seat.
        Hull.Box stabiliser = new Hull.Box(-1.42, 1.44, -6.38, 1.42, 1.53, -5.63);
        assertSpot(1.3 + RIDER + Exit.GAP, 1.75, Exit.doors(0.55, 1.75, List.of(stabiliser), 1.3, RIDER).get(0));
    }

    @Test
    void aBoxEndingWhereTheRiderBeginsIsNotAtTheDoorButOneOverlappingIs() {
        Hull.Box touching = new Hull.Box(-2.0, 0.0, 0.0, 2.0, 1.0, 1.75 - RIDER);
        assertSpot(1.3 + RIDER + Exit.GAP, 1.75, Exit.doors(0.55, 1.75, List.of(touching), 1.3, RIDER).get(0));
        Hull.Box overlapping = new Hull.Box(-2.0, 0.0, 0.0, 2.0, 1.0, 1.75 - RIDER + 0.01);
        assertSpot(2.0 + RIDER + Exit.GAP, 1.75, Exit.doors(0.55, 1.75, List.of(overlapping), 1.3, RIDER).get(0));
    }

    @Test
    void eachSideIsClearOfItsOwnReach() {
        // A box out to 2.0 on the left and only 0.5 on the right.
        Hull.Box sponson = new Hull.Box(-0.5, 0.0, 0.0, 2.0, 1.0, 3.0);
        List<Exit.Spot> d = Exit.doors(0.55, 1.75, List.of(sponson), 1.3, RIDER);
        assertSpot(2.0 + RIDER + Exit.GAP, 1.75, d.get(0));
        assertSpot(-(1.3 + RIDER + Exit.GAP), 1.75, d.get(1));
    }

    @Test
    void aNegativeHalfWidthOrANaNIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Exit.doors(0.5, 1.0, List.of(), -0.1, RIDER));
        assertThrows(IllegalArgumentException.class, () -> Exit.doors(0.5, 1.0, List.of(), 1.3, -0.3));
        assertThrows(IllegalArgumentException.class, () -> Exit.doors(Double.NaN, 1.0, List.of(), 1.3, RIDER));
    }
}
