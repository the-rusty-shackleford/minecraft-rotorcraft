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
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * An aircraft's keys, Immersive Aircraft's defaults (Rusty's call): Space climbs, Left Shift
 * descends, R gets you out; G works the hook and V the crop sprayer. All live only aboard an
 * aircraft, in a context of their own, so Space and Shift keep their jobs everywhere else. The
 * stick and the pedals are the movement keys, as in a car.
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
    public static final KeyMapping ASCEND = new KeyMapping("key.rotorcraft.ascend", AIRBORNE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
    public static final KeyMapping DESCEND = new KeyMapping("key.rotorcraft.descend", AIRBORNE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);
    public static final KeyMapping GET_OUT = new KeyMapping("key.rotorcraft.get_out", AIRBORNE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping HOOK = new KeyMapping("key.rotorcraft.hook", AIRBORNE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping SPRAY = new KeyMapping("key.rotorcraft.spray", AIRBORNE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
}
