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

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The protocol's public names: the two registries an aircraft mod writes into, keyed by the same
 * id as the Vanilla Wheels profile they complete, and the block tag the crop sprayer doses. An
 * aircraft is a Vanilla Wheels vehicle (its look, seats, cargo, fuel, paint) whose id is also in
 * {@link #AIRCRAFT}: how it flies. A slung load is one whose id is in {@link #SLING_LOADS}: where
 * its rope meets it. This protocol's entity types make both (Vanilla Wheels' D-0030).
 */
public final class Rotorcraft {
    private Rotorcraft() {}

    public static final String MOD_ID = "rotorcraft";

    /** The synced datapack registry of aircraft: {@code data/<ns>/rotorcraft/aircraft/<name>.json}. */
    public static final ResourceKey<Registry<AircraftProfile>> AIRCRAFT = ResourceKey.createRegistryKey(id("aircraft"));
    /** The synced datapack registry of slung loads: {@code data/<ns>/rotorcraft/sling_load/<name>.json}. */
    public static final ResourceKey<Registry<SlingProfile>> SLING_LOADS = ResourceKey.createRegistryKey(id("sling_load"));
    /** The blocks the crop sprayer doses: {@code #minecraft:crops} unless a datapack says more. */
    public static final TagKey<Block> SPRAYABLE = TagKey.create(Registries.BLOCK, id("sprayable"));

    /** effects: returns {@code rotorcraft:path} */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** effects: returns how the aircraft {@code vehicle} flies, if it is one */
    public static Optional<AircraftProfile> aircraft(HolderLookup.Provider registries, ResourceLocation vehicle) {
        return registries.lookup(AIRCRAFT).flatMap(r -> r.get(ResourceKey.create(AIRCRAFT, vehicle))).map(Holder.Reference::value);
    }

    /** effects: returns where the rope meets the slung load {@code vehicle}, if it is one */
    public static Optional<SlingProfile> slingLoad(HolderLookup.Provider registries, ResourceLocation vehicle) {
        return registries.lookup(SLING_LOADS).flatMap(r -> r.get(ResourceKey.create(SLING_LOADS, vehicle))).map(Holder.Reference::value);
    }
}
