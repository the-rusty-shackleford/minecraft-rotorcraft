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

import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Vec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Where a slung load's rope meets it, beside the Vanilla Wheels profile of the same id that says
 * what it is: its lift eye (mesh units, that profile's frame) and the rope's length, blocks, from
 * the eye to the aircraft's hook.
 *
 * <p>RI: 1 <= rope <= 32.
 */
public record SlingProfile(Vec lift, double rope) {
    public static final Codec<SlingProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            VehicleProfile.VEC.fieldOf("lift").forGetter(SlingProfile::lift),
            Codec.doubleRange(1.0, 32.0).optionalFieldOf("rope", 4.0).forGetter(SlingProfile::rope)
    ).apply(i, SlingProfile::new));
}
