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
package com.chunkworks.rotorcraft;

import com.chunkworks.rotorcraft.api.AircraftProfile;
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.rotorcraft.api.SlingProfile;
import com.chunkworks.rotorcraft.net.Payloads;
import com.chunkworks.vanillawheels.api.VehicleKinds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The entry point. Rotorcraft is a protocol layered on Vanilla Wheels (its D-0030): an aircraft
 * is a Vanilla Wheels vehicle whose id is also in this protocol's aircraft registry, and this mod's
 * entity type makes it; likewise a slung load. Aircraft mods ship data and nothing else.
 */
@Mod(Rotorcraft.MOD_ID)
public final class RotorcraftMod {
    public RotorcraftMod(IEventBus modBus) {
        RotorcraftContent.register(modBus);
        modBus.addListener(Payloads::register);
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(Rotorcraft.AIRCRAFT, AircraftProfile.CODEC, AircraftProfile.CODEC);
            event.dataPackRegistry(Rotorcraft.SLING_LOADS, SlingProfile.CODEC, SlingProfile.CODEC);
        });
        modBus.addListener(RotorcraftContent::buildCreativeTabs);
        VehicleKinds.register(new VehicleKinds.Kind((registries, id) -> Rotorcraft.aircraft(registries, id).isPresent(), RotorcraftContent.AIRCRAFT));
        VehicleKinds.register(new VehicleKinds.Kind((registries, id) -> Rotorcraft.slingLoad(registries, id).isPresent(), RotorcraftContent.SLUNG_LOAD));
    }
}
