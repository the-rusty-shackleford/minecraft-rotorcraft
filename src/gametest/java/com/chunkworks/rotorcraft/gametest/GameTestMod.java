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
package com.chunkworks.rotorcraft.gametest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;

/**
 * The test mod: it ships the box helicopter and the box crate (profiles, meshes, a texture) as an
 * aircraft mod would, so the protocol is proven against aircraft that exist only for the tests;
 * the gametests and the booth ride in it. Never shipped.
 */
@Mod(GameTestMod.MOD_ID)
public final class GameTestMod {
    public static final String MOD_ID = "rotorcraft_gametest";

    /**
     * Blocks where a bone meal event is cancelled, as a claim mod would cancel it: a test adds the
     * positions it protects and removes them when done; the tests run side by side, so a flag for
     * all would leak into the others.
     */
    public static final Set<Long> PROTECTED = ConcurrentHashMap.newKeySet();

    public GameTestMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((BonemealEvent event) -> {
            if (PROTECTED.contains(event.getPos().asLong())) {
                event.setCanceled(true);
            }
        });
    }

    /** effects: protects {@code pos} from bone meal until {@link #unprotect} */
    public static void protect(BlockPos pos) {
        PROTECTED.add(pos.asLong());
    }

    public static void unprotect(BlockPos pos) {
        PROTECTED.remove(pos.asLong());
    }
}
