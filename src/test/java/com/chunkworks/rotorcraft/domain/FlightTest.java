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
 * Partitions.
 * <ul>
 * <li>Spool: powered from stopped (full in spoolTicks), unpowered (down at half the rate); below
 *     {@link Flight#LIFT}, no climb with the collective up.</li>
 * <li>Vertical, lifting: collective up (to the climb rate and no more), down high up (to the
 *     descent rate), let go (to a hover); on the ground with it down (no sink).</li>
 * <li>The cushion: held down from 0.5 to 300 blocks, starting at rest or at full descent, lands at
 *     no more than 0.2, under {@link Flight#CRASH}'s safe sink; autorotating too.</li>
 * <li>Unpowered in the air: sinks at {@link Flight#AUTOROTATION}, glides at {@link Flight#GLIDE} of
 *     top speed, still turns.</li>
 * <li>Horizontal, lifting: stick forward (to top speed and no more), back (to the reverse speed),
 *     let go (to a stop within top speed / brake ticks); a sideways speed braked away; a turn at
 *     no more than the yaw rate with the velocity along the heading after it. On the ground not
 *     lifting: no motion, no turn.</li>
 * <li>Tilt: nose down accelerating and cruising (to {@link Flight#CRUISE_PITCH} of the tilt), up
 *     braking; banked right in a right turn; never past the tilt; level on the ground.</li>
 * <li>Struck: a wall met square (nothing left), at a slant (the slide along it kept), a landing
 *     (the sink gone, the glide kept), an unhindered move (unchanged).</li>
 * <li>Rep: spool, heading, tilt and finiteness refused out of range; the input's and the
 *     airframe's.</li>
 * </ul>
 */
final class FlightTest {
    private static final Airframe A = Airframe.huey();
    private static final double EPS = 1e-9;

    /** effects: returns the flight after {@code n} steps of {@code in} from {@code f} */
    private static Flight run(Flight f, FlightInput in, int n) {
        for (int i = 0; i < n; i++) {
            f = f.step(in, A);
        }
        return f;
    }

    /** effects: returns a flight spooled up and hovering high, pointing along {@code heading} */
    private static Flight hovering(double heading) {
        return Flight.landed(heading).spooled(1.0);
    }

    private static FlightInput high(int forward, int turn, int lift) {
        return new FlightInput(forward, turn, lift, true, false, 100.0);
    }

    // --- the spool -----------------------------------------------------------

    @Test
    void aPoweredRotorSpoolsUpInItsTicksAndAnUnpoweredOneRunsDownAtHalfTheRate() {
        Flight f = Flight.landed(0);
        FlightInput sit = new FlightInput(0, 0, 0, true, true, 0.0);
        f = run(f, sit, A.spoolTicks() - 1);
        assertTrue(f.spool() < 1.0, "not yet full one tick short: " + f.spool());
        f = f.step(sit, A);
        assertEquals(1.0, f.spool(), EPS, "full after spoolTicks");
        f = run(f, FlightInput.unpiloted(true, 0.0), A.spoolTicks());
        assertEquals(0.5, f.spool(), 1e-6, "half gone after as many ticks unpowered");
    }

    @Test
    void belowLiftItWillNotClimbWithTheCollectiveUp() {
        FlightInput up = new FlightInput(0, 0, 1, true, true, 0.0);
        Flight f = Flight.landed(0);
        int ticks = 0;
        while (f.spool() < Flight.LIFT - EPS) {
            assertEquals(0.0, f.vy(), EPS, "no climb at spool " + f.spool());
            f = f.step(up, A);
            ticks++;
        }
        assertTrue(ticks > 1, "it spooled for some ticks first");
        assertTrue(f.vy() > 0, "and climbs once lifting");
    }

    // --- up and down ------------------------------------------------------------

    @Test
    void theCollectiveAimsAtTheClimbTheDescentOrAHover() {
        Flight up = run(hovering(0), high(0, 0, 1), 100);
        assertEquals(A.climbRate(), up.vy(), EPS, "climbs at the climb rate and no faster");
        Flight down = run(hovering(0), high(0, 0, -1), 100);
        assertEquals(-A.descentRate(), down.vy(), EPS, "descends at the descent rate high up");
        int toHover = (int) Math.ceil(A.climbRate() / A.verticalAcceleration());
        Flight held = run(up, high(0, 0, 0), toHover);
        assertEquals(0.0, held.vy(), EPS, "let go, it holds its height within " + toHover + " ticks");
        Flight sitting = run(Flight.landed(0).spooled(1.0), new FlightInput(0, 0, -1, true, true, 0.0), 20);
        assertEquals(0.0, sitting.vy(), EPS, "on the ground the collective down sinks nothing");
    }

    /** effects: returns the sink it meets the floor at, descending under {@code in} from {@code clearance} at sink {@code v0} */
    private static double touchdown(double clearance, double v0, boolean powered) {
        Flight f = Flight.landed(0).spooled(powered ? 1.0 : 0.0).moving(0, -v0, 0);
        double h = clearance;
        for (int i = 0; i < 100_000; i++) {
            f = f.step(new FlightInput(0, 0, -1, powered, false, h), A);
            if (h + f.vy() <= 0) {
                return -f.vy();
            }
            h += f.vy();
        }
        throw new AssertionError("never landed from " + clearance);
    }

    @Test
    void heldDownFromAnyHeightItMeetsTheFloorSoftly() {
        double worst = 0.0;
        for (double h : new double[] {0.5, 1, 2, 3.7, 5, 10, 20, 50, 100, 300}) {
            double allowed = Math.min(A.descentRate(), Flight.sinkAllowed(h, A));
            for (double v0 : new double[] {0.0, allowed}) {
                double sink = touchdown(h, v0, true);
                worst = Math.max(worst, sink);
                assertTrue(sink <= 0.2, "from " + h + " blocks sinking " + v0 + ": met the floor at " + sink);
                assertEquals(0, Flight.CRASH.touchdown(sink), "a landing, not a crash: " + sink);
            }
            assertTrue(touchdown(h, 0.0, false) <= 0.2, "autorotating from " + h);
        }
        assertTrue(worst > Flight.TOUCHDOWN - 0.01, "the test reached the touchdown at all: " + worst);
        assertEquals(0, Flight.CRASH.touchdown(Flight.TOUCHDOWN), "the capped sink is a landing, with room for the step's lag");
        assertTrue(Flight.CRASH.touchdownSafe() > Flight.TOUCHDOWN, "the crash's safe sink is over the touchdown");
    }

    // --- unpowered -------------------------------------------------------------------

    @Test
    void unpoweredInTheAirItAutorotatesGlidesAndStillTurns() {
        FlightInput dead = new FlightInput(1, 1, 1, false, false, 100.0);
        Flight f = run(hovering(0), dead, 200);
        assertEquals(-Flight.AUTOROTATION, f.vy(), EPS, "sinks at the autorotation rate whatever the collective");
        assertEquals(A.maxSpeed() * Flight.GLIDE, f.along(), 1e-6, "glides at its share of top speed");
        assertTrue(Math.abs(f.heading()) > 0.1, "and the pedal still turns it: " + f.heading());
    }

    // --- along, across, the turn ------------------------------------------------------

    @Test
    void theStickDrivesItAlongItsHeadingAndLetGoItBrakesToAHover() {
        Flight fwd = run(hovering(0.7), high(1, 0, 0), 200);
        assertEquals(A.maxSpeed(), fwd.along(), EPS, "to top speed and no more");
        assertEquals(A.maxSpeed(), fwd.speed(), EPS, "all of it along the heading");
        Flight back = run(hovering(0.7), high(-1, 0, 0), 200);
        assertEquals(-A.reverseSpeed(), back.along(), EPS, "backward to the reverse speed");
        int toStop = (int) Math.ceil(A.maxSpeed() / A.brake());
        Flight stopped = run(fwd, high(0, 0, 0), toStop);
        assertEquals(0.0, stopped.speed(), EPS, "let go, it is hovering within " + toStop + " ticks");
    }

    @Test
    void aSidewaysSpeedIsBrakedAwayAndAfterATurnItFliesWhereItPoints() {
        // A slide to its right at heading 0: the right is -x.
        Flight sliding = hovering(0).moving(-0.6, 0, 0);
        Flight settled = run(sliding, high(0, 0, 0), (int) Math.ceil(0.6 / A.brake()));
        assertEquals(0.0, settled.speed(), EPS, "the slide braked away");
        Flight cruise = run(hovering(0), high(1, 0, 0), 200);
        Flight turning = cruise.step(high(1, 1, 0), A);
        assertTrue(turning.yawRate() > 0 && turning.yawRate() <= A.yawRate() + EPS, "turns right no faster than the yaw rate: " + turning.yawRate());
        Flight turned = run(cruise, high(1, 1, 0), 30);
        double fx = -Math.sin(turned.heading()), fz = Math.cos(turned.heading());
        assertEquals(turned.speed(), turned.vx() * fx + turned.vz() * fz, 1e-9, "the velocity is along the new heading");
        assertEquals(A.yawRate(), turned.yawRate(), EPS, "the turn reached the yaw rate");
    }

    @Test
    void onTheGroundNotLiftingItNeitherMovesNorTurns() {
        Flight f = run(Flight.landed(0), new FlightInput(1, 1, 0, false, true, 0.0), 40);
        assertEquals(0.0, f.speed(), EPS);
        assertEquals(0.0, f.heading(), EPS);
        Flight spooling = run(Flight.landed(0), new FlightInput(1, 1, 0, true, true, 0.0), 10);
        assertEquals(0.0, spooling.speed(), EPS, "nor while spooling up");
    }

    // --- the tilt ----------------------------------------------------------------------

    @Test
    void theNoseDipsWithSpeedAndRisesBrakingAndItBanksIntoATurnWithinTheTilt() {
        Flight accel = run(hovering(0), high(1, 0, 0), 10);
        assertTrue(accel.pitch() > 0, "nose down accelerating: " + accel.pitch());
        Flight cruise = run(hovering(0), high(1, 0, 0), 400);
        assertEquals(Flight.CRUISE_PITCH * A.tilt(), cruise.pitch(), 1e-6, "steady at top speed, the cruise share of the tilt");
        Flight braking = run(cruise, high(0, 0, 0), 30);
        assertTrue(braking.pitch() < 0, "nose up braking: " + braking.pitch());
        Flight banked = run(cruise, high(1, 1, 0), 60);
        assertTrue(banked.roll() > 0, "right side down in a right turn: " + banked.roll());
        Flight hardest = hovering(0);
        for (int i = 0; i < 300; i++) {
            hardest = hardest.step(high(i % 80 < 40 ? 1 : -1, i % 60 < 30 ? 1 : -1, 0), A);
            assertTrue(Math.abs(hardest.pitch()) <= A.tilt() + EPS && Math.abs(hardest.roll()) <= A.tilt() + EPS, "never past the tilt");
        }
        Flight parked = run(banked, new FlightInput(0, 0, 0, false, true, 0.0), 200);
        assertEquals(0.0, parked.pitch(), 1e-6, "level on the ground");
        assertEquals(0.0, parked.roll(), 1e-6);
    }

    // --- struck ----------------------------------------------------------------------------

    @Test
    void struckItKeepsOnlyWhatDoesNotDriveIntoWhatStoppedIt() {
        Flight cruising = hovering(0).moving(1.0, 0.0, 0.0);
        Flight square = cruising.struck(1.0, 0.0, 0.0, 0.3, 0.0, 0.0);
        assertEquals(0.0, square.speed(), 1e-12, "a wall met square: nothing left, wherever in the tick");
        Flight slant = hovering(0).struck(0.6, 0.0, 0.6, 0.1, 0.0, 0.6);
        assertEquals(0.0, slant.vx(), 1e-12, "a wall across x: the part into it gone");
        assertEquals(0.6, slant.vz(), 1e-12, "the slide along it kept");
        Flight landing = hovering(0).struck(0.4, -0.15, 0.0, 0.4, -0.05, 0.0);
        assertEquals(0.0, landing.vy(), 1e-12, "a landing: the sink gone");
        assertEquals(0.4, landing.vx(), 1e-12, "the glide kept");
        Flight free = hovering(0).struck(0.3, 0.1, -0.2, 0.3, 0.1, -0.2);
        assertEquals(0.3, free.vx(), 0.0);
        assertEquals(0.1, free.vy(), 0.0);
        assertEquals(-0.2, free.vz(), 0.0, "an unhindered move: unchanged");
    }

    // --- the rep ------------------------------------------------------------------------

    @Test
    void theRepIsChecked() {
        assertThrows(IllegalArgumentException.class, () -> new Flight(0, 0, 0, 0, 0, 1.1, 0, 0), "spool past full");
        assertThrows(IllegalArgumentException.class, () -> new Flight(0, 0, 0, 4.0, 0, 0, 0, 0), "heading unwrapped");
        assertThrows(IllegalArgumentException.class, () -> new Flight(0, 0, 0, 0, 0, 0, Math.PI / 2, 0), "a right angle of pitch");
        assertThrows(IllegalArgumentException.class, () -> new Flight(Double.NaN, 0, 0, 0, 0, 0, 0, 0), "not finite");
        assertThrows(IllegalArgumentException.class, () -> new FlightInput(2, 0, 0, true, true, 0), "a stick past full");
        assertThrows(IllegalArgumentException.class, () -> new FlightInput(0, 0, 0, true, true, -1), "below the floor");
        assertThrows(IllegalArgumentException.class, () -> new Airframe(1, 2, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 10, 0.1), "reverse past forward");
        assertThrows(IllegalArgumentException.class, () -> new Airframe(1, 0.5, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0, 0.1), "no spool ticks");
        assertEquals(Math.PI, Flight.landed(-Math.PI).heading(), EPS, "-pi wraps to pi");
    }
}
