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
 * What the pilot is doing this tick, and what the world allows: the stick, the pedals and the
 * collective, whether the engine can drive the rotor, whether the aircraft stands on something,
 * and how far below it the floor is.
 *
 * <p>RI: forward, turn and lift are -1, 0 or 1; clearance is finite and not negative.
 * AF: forward 1 is the stick pushed forward (W), -1 pulled back (S); turn 1 is the right pedal (D),
 *     -1 the left (A); lift 1 is the collective up (ascend), -1 down (descend); powered says the
 *     engine turns the rotor (a pilot aboard, fuel, the aircraft not a wreck); onGround says it
 *     stands on something; clearance is the height of its underside over the floor below it --
 *     the ground, or water, which it settles on -- in blocks.
 */
public record FlightInput(int forward, int turn, int lift, boolean powered, boolean onGround, double clearance) {
    public FlightInput {
        if (forward < -1 || forward > 1 || turn < -1 || turn > 1 || lift < -1 || lift > 1) {
            throw new IllegalArgumentException("forward, turn and lift are -1, 0 or 1: " + forward + ", " + turn + ", " + lift);
        }
        if (!Double.isFinite(clearance) || clearance < 0) {
            throw new IllegalArgumentException("clearance is finite and not negative: " + clearance);
        }
    }

    /** effects: returns the input of an aircraft nobody flies: hands off, the engine not driving the rotor */
    public static FlightInput unpiloted(boolean onGround, double clearance) {
        return new FlightInput(0, 0, 0, false, onGround, clearance);
    }

    /** effects: returns this input with the world's facts replaced: whether the engine drives the rotor, the ground contact, the clearance */
    public FlightInput in(boolean powered, boolean onGround, double clearance) {
        return new FlightInput(forward, turn, lift, powered, onGround, clearance);
    }
}
