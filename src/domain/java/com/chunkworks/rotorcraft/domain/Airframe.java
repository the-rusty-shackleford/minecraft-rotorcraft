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
 * What a rotorcraft can do, in blocks, ticks and radians: its speeds forward and back, how fast it
 * gains and sheds them, its climb and descent, its turn, how long its rotor takes to spool up, and
 * how far its body tilts. An aircraft's profile boils down to one of these; {@link Flight} reads
 * nothing else.
 *
 * <p>RI: every number finite; maxSpeed, acceleration, brake, climbRate, descentRate,
 *     verticalAcceleration, yawRate > 0; 0 <= reverseSpeed <= maxSpeed; spoolTicks >= 1;
 *     0 <= tilt < pi/2.
 * AF: the meaning of each number is its javadoc.
 *
 * @param maxSpeed             the fastest forward speed, blocks per tick
 * @param reverseSpeed         the fastest backward speed, blocks per tick
 * @param acceleration         speed gained along the heading per tick with the stick held
 * @param brake                speed shed per tick toward a hover with the stick let go, or held against the motion
 * @param climbRate            the fastest climb, blocks per tick
 * @param descentRate          the fastest descent, blocks per tick, high above the ground
 * @param verticalAcceleration vertical speed gained or shed per tick
 * @param yawRate              the fastest turn, radians per tick, the pedal held
 * @param spoolTicks           ticks for a stopped rotor to spool up to full speed
 * @param tilt                 the most the body pitches or banks, radians
 */
public record Airframe(double maxSpeed, double reverseSpeed, double acceleration, double brake,
                       double climbRate, double descentRate, double verticalAcceleration,
                       double yawRate, int spoolTicks, double tilt) {
    public Airframe {
        require(Double.isFinite(maxSpeed) && maxSpeed > 0, "maxSpeed must be positive");
        require(Double.isFinite(reverseSpeed) && reverseSpeed >= 0 && reverseSpeed <= maxSpeed, "reverseSpeed must be 0..maxSpeed");
        require(Double.isFinite(acceleration) && acceleration > 0, "acceleration must be positive");
        require(Double.isFinite(brake) && brake > 0, "brake must be positive");
        require(Double.isFinite(climbRate) && climbRate > 0, "climbRate must be positive");
        require(Double.isFinite(descentRate) && descentRate > 0, "descentRate must be positive");
        require(Double.isFinite(verticalAcceleration) && verticalAcceleration > 0, "verticalAcceleration must be positive");
        require(Double.isFinite(yawRate) && yawRate > 0, "yawRate must be positive");
        require(spoolTicks >= 1, "spoolTicks must be at least 1");
        require(Double.isFinite(tilt) && tilt >= 0 && tilt < Math.PI / 2, "tilt must be 0..pi/2");
    }

    private static void require(boolean ok, String what) {
        if (!ok) {
            throw new IllegalArgumentException(what);
        }
    }

    /** The test helicopter's numbers, roughly a Huey's: 1.4 b/t forward, 0.4 up, 0.5 down, 3 degrees a tick. */
    public static Airframe huey() {
        return new Airframe(1.4, 0.4, 0.03, 0.04, 0.4, 0.5, 0.03, Math.toRadians(3.0), 60, Math.toRadians(12.0));
    }
}
