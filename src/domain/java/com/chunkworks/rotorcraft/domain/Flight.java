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

import com.chunkworks.vanillawheels.domain.Crash;

/**
 * How a rotorcraft moves: its state one tick to the next under a pilot's {@link FlightInput} and an
 * {@link Airframe}. An arcade helicopter: the rotor spools up before it will climb; the collective
 * aims the vertical speed at a climb, a descent or a hover, and with nothing held it holds its
 * height; the stick drives it along its heading and, let go, brakes it to a hover; the pedals turn
 * it, and its velocity turns with it, so it never slides sideways for long. It cannot meet the
 * floor hard while the rotor turns or autorotates: its descent is capped by its height, at the
 * speed from which it can still slow to {@link #TOUCHDOWN} before the floor ({@link #sinkAllowed}).
 * Unpowered in the air -- out of fuel, the pilot gone, the aircraft a wreck -- it autorotates down
 * at {@link #AUTOROTATION}, still steering. Its body tilts nose-down with speed and acceleration
 * and banks into a turn, for drawing.
 *
 * <p>RI: every component finite; 0 <= spool <= 1; heading in (-pi, pi]; |pitch| < pi/2 and
 *     |roll| < pi/2.
 * AF: AF(vx, vy, vz, heading, yawRate, spool, pitch, roll) = "moving at (vx, vy, vz) blocks a tick
 *     in the world's frame, pointing along {@code heading} (the game's yaw: 0 is +z, growing
 *     clockwise seen from above, so the nose is at (-sin h, cos h)), turning {@code yawRate}
 *     radians a tick (positive to the right), the rotor at {@code spool} of its full speed, the body
 *     drawn nose-down by {@code pitch} and right side down by {@code roll}".
 */
public record Flight(double vx, double vy, double vz, double heading, double yawRate, double spool, double pitch, double roll) {

    /** The share of its full speed the rotor must turn at before the aircraft will climb or fly. */
    public static final double LIFT = 0.9;
    /** The sink, blocks a tick, it meets the floor at when it descends as fast as it is allowed. */
    public static final double TOUCHDOWN = 0.15;
    /** The share of the airframe's vertical acceleration the descent cap plans to brake with: the rest is margin for the step's one tick of lag. */
    public static final double CUSHION = 0.8;
    /** How fast it sinks unpowered in the air, blocks a tick: an autorotation. */
    public static final double AUTOROTATION = 0.25;
    /** The share of its top speed it glides at, unpowered. */
    public static final double GLIDE = 0.6;
    /**
     * What a crash costs an aircraft or the load on its hook (Vanilla Wheels' {@link Crash}, its
     * D-0031; here as {@code Wear} until 1.1.0): a wall or a roof met faster than 0.25 blocks a tick,
     * the floor met sinking faster than 0.3 -- over {@link #TOUCHDOWN}, with room for the step's lag
     * -- and a wreck at 1.5.
     */
    public static final Crash CRASH = new Crash(0.25, 0.3, 1.5);
    /** The share of the airframe's yaw rate the turn gains or sheds a tick. */
    public static final double YAW_EASE = 0.2;
    /** The share of the way to its target tilt the body moves a tick. */
    public static final double TILT_EASE = 0.15;
    /** The share of the tilt the nose is down in steady flight at top speed; the rest answers acceleration. */
    public static final double CRUISE_PITCH = 0.6;
    /** The share of the bank a pedal turn in a hover gets; it grows to all of it at top speed. */
    public static final double BANK_AT_HOVER = 0.3;

    public Flight {
        if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz) || !Double.isFinite(heading)
                || !Double.isFinite(yawRate) || !Double.isFinite(spool) || !Double.isFinite(pitch) || !Double.isFinite(roll)) {
            throw new IllegalArgumentException("a flight is finite");
        }
        if (spool < 0 || spool > 1) {
            throw new IllegalArgumentException("spool is 0..1: " + spool);
        }
        if (heading <= -Math.PI || heading > Math.PI) {
            throw new IllegalArgumentException("heading is in (-pi, pi]: " + heading);
        }
        if (Math.abs(pitch) >= Math.PI / 2 || Math.abs(roll) >= Math.PI / 2) {
            throw new IllegalArgumentException("pitch and roll are within a right angle: " + pitch + ", " + roll);
        }
    }

    /** effects: returns an aircraft standing still, rotor stopped, pointing along {@code heading} (wrapped) */
    public static Flight landed(double heading) {
        return new Flight(0, 0, 0, wrap(heading), 0, 0, 0, 0);
    }

    /** effects: returns this flight with the rotor at {@code spool}; requires 0 <= spool <= 1 */
    public Flight spooled(double spool) {
        return new Flight(vx, vy, vz, heading, yawRate, spool, pitch, roll);
    }

    /** effects: returns this flight moving at (vx, vy, vz) instead: what a collision left of its velocity */
    public Flight moving(double vx, double vy, double vz) {
        return new Flight(vx, vy, vz, heading, yawRate, spool, pitch, roll);
    }

    /**
     * effects: returns this flight after the world let it move only (gx, gy, gz) of the (wx, wy, wz)
     * it was stepped to: the velocity loses its part along the direction the move lost -- all of it
     * into a wall met square, the part into the wall of one met at a slant, the sink of a landing --
     * and keeps the rest, so it slides along what stopped it and does not drive into it again; an
     * unhindered move leaves it as it is
     */
    public Flight struck(double wx, double wy, double wz, double gx, double gy, double gz) {
        double[] v = kept(wx, wy, wz, gx, gy, gz);
        return moving(v[0], v[1], v[2]);
    }

    /** effects: returns (wx, wy, wz) less its part along (wx - gx, wy - gy, wz - gz), or itself when that is nothing */
    static double[] kept(double wx, double wy, double wz, double gx, double gy, double gz) {
        double lx = wx - gx, ly = wy - gy, lz = wz - gz;
        double loss = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (loss < 1e-9) {
            return new double[] {wx, wy, wz};
        }
        double along = (wx * lx + wy * ly + wz * lz) / loss;
        return new double[] {wx - lx / loss * along, wy - ly / loss * along, wz - lz / loss * along};
    }

    /** effects: returns the horizontal speed, blocks a tick */
    public double speed() {
        return Math.hypot(vx, vz);
    }

    /** effects: returns the speed along the heading, blocks a tick; negative flying backward */
    public double along() {
        return -Math.sin(heading) * vx + Math.cos(heading) * vz;
    }

    /**
     * requires: clearance >= 0
     * effects: returns the fastest sink allowed {@code clearance} blocks over the floor: the sink
     * from which, braking at {@link #CUSHION} of the airframe's vertical acceleration, it slows to
     * {@link #TOUCHDOWN} by the floor -- {@code sqrt(TOUCHDOWN^2 + 2 * CUSHION * a * clearance)}
     */
    public static double sinkAllowed(double clearance, Airframe a) {
        return Math.sqrt(TOUCHDOWN * TOUCHDOWN + 2.0 * CUSHION * a.verticalAcceleration() * clearance);
    }

    /**
     * effects: returns this flight one tick on under {@code in} and {@code a}:
     * <ul>
     * <li>the rotor spools up a {@code spoolTicks}th a tick while powered, down at half that
     *     otherwise; it lifts once powered at {@link #LIFT} or more;</li>
     * <li>vertical: lifting, the speed is aimed at the climb rate, the descent rate or zero by the
     *     collective; unpowered in the air at {@link #AUTOROTATION} down; on the ground at rest;
     *     the aim is never a sink faster than {@link #sinkAllowed}; the speed moves toward the aim
     *     by the vertical acceleration, and never sinks into the ground;</li>
     * <li>horizontal, lifting or in the air: the speed along the heading is aimed at the top
     *     speed (stick forward), the reverse speed (back) or zero (let go), a {@link #GLIDE} of
     *     them unpowered, and moves toward it by the acceleration -- by the brake when the stick is
     *     let go or held against the motion; any sideways speed is braked away; the turn rate
     *     eases toward the pedal's; the velocity is put back along the new heading. On the ground,
     *     not lifting: no horizontal motion and no turn;</li>
     * <li>the body's pitch eases toward the tilt's share of its speed and acceleration, its roll
     *     toward the tilt's share of its turn, both level on the ground.</li>
     * </ul>
     */
    public Flight step(FlightInput in, Airframe a) {
        double s = in.powered() ? Math.min(1.0, spool + 1.0 / a.spoolTicks()) : Math.max(0.0, spool - 0.5 / a.spoolTicks());
        boolean lifting = in.powered() && s >= LIFT;
        boolean flying = lifting || !in.onGround();

        // Up and down.
        double aim;
        if (lifting) {
            aim = in.lift() > 0 ? a.climbRate() : in.lift() < 0 ? -a.descentRate() : 0.0;
        } else if (!in.onGround()) {
            aim = -AUTOROTATION;
        } else {
            aim = 0.0;
        }
        aim = Math.max(aim, -sinkAllowed(in.clearance(), a));
        double y = approach(vy, aim, a.verticalAcceleration());
        if (in.onGround() && y < 0) {
            y = 0.0;
        }

        // Along the heading, across it, and the turn.
        double fx = -Math.sin(heading), fz = Math.cos(heading);
        double along = vx * fx + vz * fz;
        double side = vx * -Math.cos(heading) + vz * -Math.sin(heading);
        double along2, side2, turn;
        if (flying) {
            double share = lifting ? 1.0 : GLIDE;
            double target = in.forward() > 0 ? a.maxSpeed() * share : in.forward() < 0 ? -a.reverseSpeed() * share : 0.0;
            boolean against = in.forward() != 0 && along != 0 && Math.signum(target) != Math.signum(along);
            along2 = approach(along, target, in.forward() == 0 || against ? a.brake() : a.acceleration());
            side2 = approach(side, 0.0, a.brake());
            turn = approach(yawRate, in.turn() * a.yawRate(), a.yawRate() * YAW_EASE);
        } else {
            along2 = 0.0;
            side2 = 0.0;
            turn = 0.0;
        }
        double h = wrap(heading + turn);
        double x = -Math.sin(h) * along2 - Math.cos(h) * side2;
        double z = Math.cos(h) * along2 - Math.sin(h) * side2;

        // The body's tilt.
        double pitchTo = 0.0, rollTo = 0.0;
        if (flying) {
            double accel = along2 - along;
            pitchTo = a.tilt() * clamp(CRUISE_PITCH * along2 / a.maxSpeed() + (1.0 - CRUISE_PITCH) * accel / a.acceleration());
            rollTo = a.tilt() * clamp(turn / a.yawRate() * (BANK_AT_HOVER + (1.0 - BANK_AT_HOVER) * Math.min(1.0, Math.abs(along2) / a.maxSpeed())));
        }
        double p = pitch + (pitchTo - pitch) * TILT_EASE;
        double r = roll + (rollTo - roll) * TILT_EASE;
        return new Flight(x, y, z, h, turn, s, p, r);
    }

    /** effects: returns {@code from} moved toward {@code to} by at most {@code by}; requires by >= 0 */
    static double approach(double from, double to, double by) {
        return from < to ? Math.min(to, from + by) : Math.max(to, from - by);
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    /** effects: returns {@code a} wrapped into (-pi, pi] */
    public static double wrap(double a) {
        double w = a % (2 * Math.PI);
        if (w <= -Math.PI) {
            w += 2 * Math.PI;
        } else if (w > Math.PI) {
            w -= 2 * Math.PI;
        }
        return w;
    }
}
