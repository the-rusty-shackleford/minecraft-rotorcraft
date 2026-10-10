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
 * is a lever that the Up and Down keys move and that stays where it is let go (Rusty, 2026-10-10,
 * after Immersive Aircraft's engine target): in its detent at the middle it holds the height, and
 * above or below it the climb or the descent grows with how far it is from the detent. The stick
 * tilts the nose further the longer it is held, a tap creeping and a held key full speed, and let
 * go it springs back level and brakes to a hover; the pedals turn
 * it, and its velocity turns with it, so it never slides sideways for long. It cannot meet the
 * floor hard while the rotor turns or autorotates: its descent is capped by its height, at the
 * speed from which it can still slow to {@link #TOUCHDOWN} before the floor ({@link #sinkAllowed}).
 * Unpowered in the air -- out of fuel, the pilot gone, the aircraft a wreck -- it autorotates down
 * at {@link #AUTOROTATION}, still steering. Its body tilts nose-down with speed and acceleration
 * and banks into a turn, for drawing.
 *
 * <p>RI: every component finite; 0 <= spool <= 1; heading in (-pi, pi]; |pitch| < pi/2 and
 *     |roll| < pi/2; 0 <= collective <= 1; -1 <= stick <= 1.
 * AF: AF(vx, vy, vz, heading, yawRate, spool, pitch, roll, collective, stick) = "moving at
 *     (vx, vy, vz) blocks a tick in the world's frame, pointing along {@code heading} (the game's
 *     yaw: 0 is +z, growing clockwise seen from above, so the nose is at (-sin h, cos h)), turning
 *     {@code yawRate} radians a tick (positive to the right), the rotor at {@code spool} of its full
 *     speed, the body drawn nose-down by {@code pitch} and right side down by {@code roll}, the
 *     collective lever at {@code collective} of its travel (bottom 0, {@link #HOVER} its detent,
 *     top 1), the stick tilted {@code stick} of its way forward (negative back)".
 */
public record Flight(double vx, double vy, double vz, double heading, double yawRate, double spool, double pitch, double roll,
                     double collective, double stick) {

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
    /** The middle of the collective's detent: where the lever holds the height, and where it stands when a pilot takes the controls. */
    public static final double HOVER = 0.5;

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
        if (!Double.isFinite(collective) || !Double.isFinite(stick) || collective < 0 || collective > 1 || Math.abs(stick) > 1) {
            throw new IllegalArgumentException("the collective is 0..1 and the stick -1..1: " + collective + ", " + stick);
        }
    }

    /** effects: returns an aircraft standing still, rotor stopped, pointing along {@code heading} (wrapped), the collective in its detent and the stick level */
    public static Flight landed(double heading) {
        return new Flight(0, 0, 0, wrap(heading), 0, 0, 0, 0, HOVER, 0);
    }

    /** effects: returns this flight with the rotor at {@code spool}; requires 0 <= spool <= 1 */
    public Flight spooled(double spool) {
        return new Flight(vx, vy, vz, heading, yawRate, spool, pitch, roll, collective, stick);
    }

    /** effects: returns this flight moving at (vx, vy, vz) instead: what a collision left of its velocity */
    public Flight moving(double vx, double vy, double vz) {
        return new Flight(vx, vy, vz, heading, yawRate, spool, pitch, roll, collective, stick);
    }

    /** effects: returns this flight with the collective lever put at {@code lever}: a script's hand on it; requires 0 <= lever <= 1 */
    public Flight withCollective(double lever) {
        return new Flight(vx, vy, vz, heading, yawRate, spool, pitch, roll, lever, stick);
    }

    /**
     * requires: min <= v <= max; dir is -1, 0 or 1; low <= high; 0 < slow <= rate
     * effects: returns {@code v} a tick on, moved toward {@code max} (dir 1) or {@code min} (-1) by
     *     {@code rate}, by {@code slow} between {@code low} and {@code high} -- exactly: a tick's
     *     travel that reaches either edge goes on at the other rate, and one that reaches an edge
     *     to within rounding stops on it -- never past min or max; dir 0, where it is
     */
    static double travel(double v, int dir, double min, double max, double low, double high, double rate, double slow) {
        if (dir == 0) {
            return v;
        }
        double time = 1.0;
        while (time > 1e-12) {
            boolean slowed = dir > 0 ? v >= low && v < high : v > low && v <= high;
            double r = slowed ? slow : rate;
            double edge = dir > 0 ? (v < low ? low : v < high ? high : max) : (v > high ? high : v > low ? low : min);
            double reach = Math.abs(edge - v) / r;
            if (reach > time + 1e-9) {
                return v + dir * r * time;
            }
            v = edge;
            time -= reach;
            if (edge == min || edge == max) {
                return v;
            }
        }
        return v;
    }

    /**
     * requires: 0 <= lever <= 1; lift is -1, 0 or 1
     * effects: returns the collective a tick on with its key {@code lift} held: up or down by
     *     {@code c}'s lever rate, by its detent rate through the detent, never past the bottom or
     *     the top ({@link #travel}); let go, where it is
     */
    public static double lever(double lever, int lift, Controls c) {
        return travel(lever, lift, 0.0, 1.0, HOVER - c.detent(), HOVER + c.detent(), c.leverRate(), c.detentRate());
    }

    /**
     * requires: 0 <= lever <= 1
     * effects: returns the share of the climb (positive) or the descent (negative) the collective at
     *     {@code lever} asks for, -1..1: none in {@code c}'s detent, growing evenly to all of it at the
     *     top or the bottom
     */
    public static double climbShare(double lever, Controls c) {
        double low = HOVER - c.detent(), high = HOVER + c.detent();
        if (lever > high) {
            return (lever - high) / (1.0 - high);
        }
        if (lever < low) {
            return -(low - lever) / low;
        }
        return 0.0;
    }

    /**
     * requires: -1 <= stick <= 1; forward is -1, 0 or 1
     * effects: returns the stick a tick on with {@code forward} held. Springing back ({@code c} not
     *     {@code stickStays}): further over its way by the stick rate, to full tilt and no more; let
     *     go, or held against its tilt, back toward level by the spring. Staying: moved by the stick
     *     rate, slowed through its detent at level ({@link #travel}), and let go, where it is.
     */
    public static double stick(double stick, int forward, Controls c) {
        if (c.stickStays()) {
            return travel(stick, forward, -1.0, 1.0, -c.stickDetent(), c.stickDetent(), c.stickRate(), c.stickDetentRate());
        }
        if (forward == 0 || stick * forward < 0) {
            return approach(stick, 0.0, c.stickSpring());
        }
        return approach(stick, forward, c.stickRate());
    }

    /**
     * requires: -1 <= stick <= 1
     * effects: returns the share of the top speed (positive) or the reverse speed (negative) the
     *     stick at {@code stick} asks for, -1..1: itself, springing back; staying, none in its detent
     *     at level, growing evenly to all of it at full tilt
     */
    public static double stickShare(double stick, Controls c) {
        if (!c.stickStays()) {
            return stick;
        }
        double d = c.stickDetent();
        return Math.abs(stick) <= d ? 0.0 : Math.signum(stick) * (Math.abs(stick) - d) / (1.0 - d);
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
     * <li>the collective moves by its key ({@link #lever}), the stick by its key ({@link #stick});</li>
     * <li>vertical: lifting, the speed is aimed at the collective's share of the climb rate or the
     *     descent rate ({@link #climbShare}), zero in its detent; unpowered in the air at
     *     {@link #AUTOROTATION} down; on the ground at rest;
     *     the aim is never a sink faster than {@link #sinkAllowed}; the speed moves toward the aim
     *     by the vertical acceleration, and never sinks into the ground;</li>
     * <li>horizontal, lifting or in the air: held, the speed along the heading is aimed at the
     *     stick's share of the top speed (forward) or of the reverse speed (back), a {@link #GLIDE}
     *     of it unpowered, and moves toward it by the acceleration -- by the brake when that is
     *     against the motion; let go, or held while the stick still leans the other way, it is
     *     aimed at zero by the brake; any sideways speed is braked away; the turn rate
     *     eases toward the pedal's; the velocity is put back along the new heading. On the ground,
     *     not lifting: no horizontal motion and no turn;</li>
     * <li>the body's pitch eases toward the tilt's share of the stick and the acceleration, its
     *     roll toward the tilt's share of its turn, both level on the ground.</li>
     * </ul>
     */
    public Flight step(FlightInput in, Airframe a) {
        return step(in, a, Controls.DEFAULT);
    }

    /** effects: returns this flight one tick on under {@code in} and {@code a}, the keys moving the controls as {@code c} says (see {@link #step(FlightInput, Airframe)}) */
    public Flight step(FlightInput in, Airframe a, Controls c) {
        double s = in.powered() ? Math.min(1.0, spool + 1.0 / a.spoolTicks()) : Math.max(0.0, spool - 0.5 / a.spoolTicks());
        boolean lifting = in.powered() && s >= LIFT;
        boolean flying = lifting || !in.onGround();
        double lever = lever(collective, in.lift(), c);
        double tilt = flying ? stick(stick, in.forward(), c) : 0.0;
        double ask = stickShare(tilt, c);

        // Up and down.
        double aim;
        if (lifting) {
            double share = climbShare(lever, c);
            aim = share > 0 ? share * a.climbRate() : share * a.descentRate();
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
            // Springing back, let go or held while the stick is still coming over from the other way:
            // a brake to a hover. Staying, the stick asks for its share whatever is held.
            boolean asks = c.stickStays() || in.forward() != 0 && tilt * in.forward() > 0;
            double target = !asks ? 0.0 : (ask > 0 ? ask * a.maxSpeed() : ask * a.reverseSpeed()) * share;
            // Gaining speed the way it goes is the acceleration's; shedding it, or turning it round, the brake's.
            boolean gaining = target != 0 && (along == 0 || Math.signum(target) == Math.signum(along)) && Math.abs(target) > Math.abs(along);
            along2 = approach(along, target, gaining ? a.acceleration() : a.brake());
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
            pitchTo = a.tilt() * clamp(CRUISE_PITCH * ask + (1.0 - CRUISE_PITCH) * accel / a.acceleration());
            rollTo = a.tilt() * clamp(turn / a.yawRate() * (BANK_AT_HOVER + (1.0 - BANK_AT_HOVER) * Math.min(1.0, Math.abs(along2) / a.maxSpeed())));
        }
        double p = pitch + (pitchTo - pitch) * TILT_EASE;
        double r = roll + (rollTo - roll) * TILT_EASE;
        return new Flight(x, y, z, h, turn, s, p, r, lever, tilt);
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
