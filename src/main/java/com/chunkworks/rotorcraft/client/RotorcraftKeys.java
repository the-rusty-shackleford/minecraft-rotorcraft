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
package com.chunkworks.rotorcraft.client;

import com.chunkworks.rotorcraft.Aircraft;
import com.chunkworks.vanillawheels.client.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * An aircraft's own keys, Immersive Aircraft's defaults (Rusty's call): G works the hook and V the
 * crop sprayer. They live only aboard an aircraft, in a context of their own. Up (Space), Down
 * (Left Shift) and Get out (R) are Vanilla Wheels' since 1.1.0 (its D-0031), shared with every
 * body that moves in three dimensions; the stick and the pedals are the movement keys, as in a car.
 */
public final class RotorcraftKeys {
    private RotorcraftKeys() {}

    /** Active while the local player rides an aircraft, with no screen open. */
    public static final IKeyConflictContext AIRBORNE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.screen == null && mc.player.getVehicle() instanceof Aircraft;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };

    public static final String CATEGORY = "key.categories.rotorcraft";
    public static final KeyMapping HOOK = new Keys.RidingKey("key.rotorcraft.hook", AIRBORNE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping SPRAY = new Keys.RidingKey("key.rotorcraft.spray", AIRBORNE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /**
     * effects: lets go of the aircraft's own keys. A key's release is passed only to keys whose
     * context is live, so one let go after its rider got out would stay down into the next aircraft.
     */
    public static void releaseAll() {
        HOOK.setDown(false);
        SPRAY.setDown(false);
    }
}
