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

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Partitions. A still boom: the columns under its line, square to the heading, facing south and
 * facing west. A pass at speed, tick by tick: every column under its track wetted, where sampling
 * only each tick's line leaves dry stripes. A cooldown: a column due again only once it has
 * passed. A boom of no width refused.
 */
final class SwathTest {

    /** effects: returns the columns, as "x,z", a sweep reports */
    private static Set<String> swept(double x0, double z0, double h0, double x1, double z1, double h1, double width) {
        Set<String> out = new TreeSet<>();
        Swath.sweep(x0, z0, h0, x1, z1, h1, width, (x, z) -> out.add(x + "," + z));
        return out;
    }

    @Test
    void aStillBoomWetsTheColumnsUnderItsLineSquareToTheHeading() {
        // Facing south (+z), the boom lies east-west: x from -1.5 to 2.5 at z 0.5.
        assertEquals(Set.of("-2,0", "-1,0", "0,0", "1,0", "2,0"), swept(0.5, 0.5, 0.0, 0.5, 0.5, 0.0, 4.0));
        // Facing west (-x), it lies north-south.
        assertEquals(Set.of("0,-2", "0,-1", "0,0", "0,1", "0,2"), swept(0.5, 0.5, Math.PI / 2, 0.5, 0.5, Math.PI / 2, 4.0));
    }

    @Test
    void aPassAtSpeedWetsEveryColumnUnderItsTrackWhereEachTicksLineAloneLeavesStripes() {
        double speed = 1.4;
        Set<Integer> rows = new TreeSet<>();
        Set<Integer> linesOnly = new TreeSet<>();
        for (int i = 0; i < 10; i++) {
            double z0 = 0.5 + speed * i, z1 = z0 + speed;
            Swath.sweep(0.5, z0, 0.0, 0.5, z1, 0.0, 2.0, (x, z) -> rows.add(z));
            Swath.sweep(0.5, z1, 0.0, 0.5, z1, 0.0, 2.0, (x, z) -> linesOnly.add(z));
        }
        for (int z = 0; z <= 14; z++) {
            assertTrue(rows.contains(z), "row " + z + " wetted");
        }
        assertTrue(linesOnly.size() < 14, "a line a tick misses rows: " + linesOnly);
        assertThrows(IllegalArgumentException.class, () -> Swath.sweep(0, 0, 0, 1, 1, 0, 0.0, (x, z) -> {}));
    }

    @Test
    void aColumnIsDueAgainOnlyOnceItsCooldownHasPassed() {
        assertFalse(Swath.due(10, 49, 40));
        assertTrue(Swath.due(10, 50, 40));
        assertTrue(Swath.due(Long.MIN_VALUE / 2, 0, 40), "never dosed");
    }
}
