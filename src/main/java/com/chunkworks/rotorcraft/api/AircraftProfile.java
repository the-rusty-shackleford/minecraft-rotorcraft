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
import com.chunkworks.rotorcraft.domain.Hull;
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
 * @param hull                 the boxes the body is made of for its collisions in the air; none,
 *                             the Vanilla Wheels body's nose and tail
 */
public record AircraftProfile(double climbRate, double descentRate, double verticalAcceleration, double yawRate, int spoolTicks,
                              double tilt, List<Rotor> rotors, Optional<Vec> hook, Optional<Sprayer> sprayer, List<HullBox> hull) {

    /**
     * A part that spins: its faces turn about {@code axis} through {@code pivot} (mesh units), at
     * {@code speed} times the rotor's turn -- negative the other way round, a tail rotor faster.
     * {@code radius} (mesh units) is how far its blades reach from the pivot, so the aircraft is
     * still drawn while only a blade is in view; the blades are drawn only, and never collide.
     */
    public record Rotor(VehicleProfile.PartSelector part, Vec pivot, Vec axis, double speed, double radius) {
        public static final Codec<Rotor> CODEC = RecordCodecBuilder.create(i -> i.group(
                VehicleProfile.PartSelector.CODEC.fieldOf("part").forGetter(Rotor::part),
                VehicleProfile.VEC.fieldOf("pivot").forGetter(Rotor::pivot),
                VehicleProfile.VEC.optionalFieldOf("axis", Vec.Y).forGetter(Rotor::axis),
                Codec.doubleRange(-20.0, 20.0).optionalFieldOf("speed", 1.0).forGetter(Rotor::speed),
                Codec.doubleRange(0.0, 4096.0).optionalFieldOf("radius", 0.0).forGetter(Rotor::radius)
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

    /**
     * A box of the hull, by two opposite corners (mesh units): what meets the world in flight. A
     * long aircraft is several (a cabin, a boom, a fin), each probed all over (domain {@code Hull}).
     */
    public record HullBox(Vec from, Vec to) {
        public static final Codec<HullBox> CODEC = RecordCodecBuilder.create(i -> i.group(
                VehicleProfile.VEC.fieldOf("from").forGetter(HullBox::from),
                VehicleProfile.VEC.fieldOf("to").forGetter(HullBox::to)
        ).apply(i, HullBox::new));
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
            Sprayer.CODEC.optionalFieldOf("sprayer").forGetter(AircraftProfile::sprayer),
            HullBox.CODEC.listOf(0, 16).optionalFieldOf("hull", List.of()).forGetter(AircraftProfile::hull)
    ).apply(i, AircraftProfile::new));

    public AircraftProfile {
        rotors = List.copyOf(rotors);
        hull = List.copyOf(hull);
    }

    /** effects: returns the hull's boxes in blocks in {@code p}'s body frame, mirrored and scaled as its mesh is */
    public List<Hull.Box> hullBoxes(VehicleProfile p) {
        List<Hull.Box> out = new java.util.ArrayList<>();
        for (HullBox b : hull) {
            Vec a = p.localBlocks(b.from()), c = p.localBlocks(b.to());
            out.add(Hull.Box.spanning(a.x(), a.y(), a.z(), c.x(), c.y(), c.z()));
        }
        return List.copyOf(out);
    }

    /** effects: returns the rotors' discs in blocks in {@code p}'s body frame */
    public List<Hull.Disc> discs(VehicleProfile p) {
        List<Hull.Disc> out = new java.util.ArrayList<>();
        for (Rotor r : rotors) {
            Vec c = p.localBlocks(r.pivot());
            out.add(new Hull.Disc(c.x(), c.y(), c.z(), p.blocks(r.radius())));
        }
        return List.copyOf(out);
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
