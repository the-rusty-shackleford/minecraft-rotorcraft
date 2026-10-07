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
import net.neoforged.neoforge.client.settings.KeyConflictContext;
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
    public static final KeyMapping ASCEND = new AircraftKey("key.rotorcraft.ascend", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping DESCEND = new AircraftKey("key.rotorcraft.descend", GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping GET_OUT = new AircraftKey("key.rotorcraft.get_out", GLFW.GLFW_KEY_R);
    public static final KeyMapping HOOK = new AircraftKey("key.rotorcraft.hook", GLFW.GLFW_KEY_G);
    public static final KeyMapping SPRAY = new AircraftKey("key.rotorcraft.spray", GLFW.GLFW_KEY_V);

    private static final KeyMapping[] ALL = {ASCEND, DESCEND, GET_OUT, HOOK, SPRAY};

    /**
     * effects: lets go of every aircraft key. A key's release is passed only to keys whose context
     * is live, so one let go after its rider got out (Shift held to the ground, then R) would stay
     * down and fly the next aircraft boarded.
     */
    public static void releaseAll() {
        for (KeyMapping key : ALL) {
            key.setDown(false);
        }
    }

    /**
     * An aircraft key. NeoForge judges a key bound with no modifier as up while Shift, Control or
     * Alt is held, in every context but the game's own; Descend is Left Shift, so holding it
     * switched Descend off, and the hook, the sprayer and getting out with it. An aircraft key's
     * modifier is judged as the game's own keys' are: with none, it is down whatever else is held.
     */
    private static final class AircraftKey extends KeyMapping {
        AircraftKey(String name, int key) {
            super(name, AIRBORNE, InputConstants.Type.KEYSYM, key, CATEGORY);
        }

        @Override
        public boolean isConflictContextAndModifierActive() {
            return getKeyConflictContext().isActive() && getKeyModifier().isActive(KeyConflictContext.IN_GAME);
        }

        @Override
        public boolean isActiveAndMatches(InputConstants.Key keyCode) {
            return keyCode != InputConstants.UNKNOWN && keyCode.equals(getKey()) && isConflictContextAndModifierActive();
        }
    }
}
