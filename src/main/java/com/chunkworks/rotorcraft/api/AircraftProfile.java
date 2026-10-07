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
package com.chunkworks.rotorcraft.api;

import com.chunkworks.rotorcraft.domain.Airframe;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Vec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/**
 * How an aircraft flies, beside the Vanilla Wheels profile of the same id that says what it is.
 * Horizontal flight takes that profile's engine (its top and reverse speeds, its acceleration and
 * brake); this says the rest: the climb and the descent, the turn, the rotor's spool-up, the
 * body's tilt; the rotors, which spin; where the cargo hook is; and where a crop sprayer mounts.
 * Vectors are the Vanilla Wheels profile's: mesh units in its frame, mirrored and scaled by it.
 * Every number is bounded here, in the record and the codec, so a bad file is refused naming the
 * field.
 *
 * <p>RI: every rate positive and finite; spoolTicks >= 1; 0 <= tilt < 90 degrees.
 *
 * @param climbRate            the fastest climb, blocks a tick
 * @param descentRate          the fastest descent, blocks a tick
 * @param verticalAcceleration vertical speed gained or shed a tick
 * @param yawRate              the fastest turn, degrees a tick
 * @param spoolTicks           ticks for the rotor to spool up from a stop
 * @param tilt                 the most the body pitches or banks, degrees
 * @param rotors               the parts that spin
 * @param hook                 the cargo hook, if it carries slung loads
 * @param sprayer              where a crop sprayer mounts, if one can
 */
public record AircraftProfile(double climbRate, double descentRate, double verticalAcceleration, double yawRate, int spoolTicks,
                              double tilt, List<Rotor> rotors, Optional<Vec> hook, Optional<Sprayer> sprayer) {

    /**
     * A part that spins: its faces turn about {@code axis} through {@code pivot} (mesh units), at
     * {@code speed} times the rotor's turn -- negative the other way round, a tail rotor faster.
     */
    public record Rotor(VehicleProfile.PartSelector part, Vec pivot, Vec axis, double speed) {
        public static final Codec<Rotor> CODEC = RecordCodecBuilder.create(i -> i.group(
                VehicleProfile.PartSelector.CODEC.fieldOf("part").forGetter(Rotor::part),
                VehicleProfile.VEC.fieldOf("pivot").forGetter(Rotor::pivot),
                VehicleProfile.VEC.optionalFieldOf("axis", Vec.Y).forGetter(Rotor::axis),
                Codec.doubleRange(-20.0, 20.0).optionalFieldOf("speed", 1.0).forGetter(Rotor::speed)
        ).apply(i, Rotor::new));

        public Rotor {
            if (axis.length() < 1e-9) {
                throw new IllegalArgumentException("a rotor's axis has a direction");
            }
            axis = axis.normalized();
        }
    }

    /**
     * Where a crop sprayer mounts: the boom's faces, drawn only while one is fitted; the middle of
     * its nozzle line (mesh units); and the swath it wets, blocks across.
     */
    public record Sprayer(VehicleProfile.PartSelector part, Vec at, double width) {
        public static final Codec<Sprayer> CODEC = RecordCodecBuilder.create(i -> i.group(
                VehicleProfile.PartSelector.CODEC.fieldOf("part").forGetter(Sprayer::part),
                VehicleProfile.VEC.fieldOf("at").forGetter(Sprayer::at),
                Codec.doubleRange(1.0, 32.0).fieldOf("width").forGetter(Sprayer::width)
        ).apply(i, Sprayer::new));
    }

    public static final Codec<AircraftProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0.01, 3.0).fieldOf("climb_rate").forGetter(AircraftProfile::climbRate),
            Codec.doubleRange(0.01, 3.0).fieldOf("descent_rate").forGetter(AircraftProfile::descentRate),
            Codec.doubleRange(0.001, 0.1).optionalFieldOf("vertical_acceleration", 0.03).forGetter(AircraftProfile::verticalAcceleration),
            Codec.doubleRange(0.1, 20.0).optionalFieldOf("yaw_rate", 3.0).forGetter(AircraftProfile::yawRate),
            Codec.intRange(1, 1200).optionalFieldOf("spool_ticks", 60).forGetter(AircraftProfile::spoolTicks),
            Codec.doubleRange(0.0, 45.0).optionalFieldOf("tilt", 12.0).forGetter(AircraftProfile::tilt),
            Rotor.CODEC.listOf(0, 8).optionalFieldOf("rotors", List.of()).forGetter(AircraftProfile::rotors),
            VehicleProfile.VEC.optionalFieldOf("hook").forGetter(AircraftProfile::hook),
            Sprayer.CODEC.optionalFieldOf("sprayer").forGetter(AircraftProfile::sprayer)
    ).apply(i, AircraftProfile::new));

    public AircraftProfile {
        rotors = List.copyOf(rotors);
    }

    /**
     * requires: {@code p} is powered (it has an engine)
     * effects: returns the numbers {@link com.chunkworks.rotorcraft.domain.Flight} flies by: the
     * engine's speeds, acceleration and brake from {@code p}, the rest from here
     * throws: IllegalArgumentException for a profile with no engine
     */
    public Airframe airframe(VehicleProfile p) {
        VehicleProfile.Engine e = p.engine().orElseThrow(() -> new IllegalArgumentException("an aircraft has an engine"));
        return new Airframe(e.maxSpeed(), Math.min(e.reverseSpeed(), e.maxSpeed()), e.acceleration(), e.brake(),
                climbRate, descentRate, verticalAcceleration, Math.toRadians(yawRate), spoolTicks, Math.toRadians(tilt));
    }

    /** effects: returns the parts drawn apart from the body, in order: each rotor, then the sprayer's boom if there is a mount */
    public List<VehicleProfile.PartSelector> extras() {
        List<VehicleProfile.PartSelector> out = new java.util.ArrayList<>();
        for (Rotor r : rotors) {
            out.add(r.part());
        }
        sprayer.ifPresent(s -> out.add(s.part()));
        return List.copyOf(out);
    }
}
