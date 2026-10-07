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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A readout beside the hotbar, on its right, while riding an aircraft with a crop sprayer fitted:
 * whether it sprays, and the bone meal in its tank -- nothing on the aircraft says either.
 */
public final class SprayerIndicator {
    private SprayerIndicator() {}

    /** effects: draws the readout if the local player rides an aircraft with a sprayer fitted */
    public static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof Aircraft a) || !a.sprayerFitted()) {
            return;
        }
        Component label = Component.translatable(a.spraying() ? "rotorcraft.hud.spraying" : "rotorcraft.hud.sprayer", a.bonemeal());
        int x = g.guiWidth() / 2 + 91 + 6;
        int y = g.guiHeight() - 15;
        g.drawString(mc.font, label, x, y, a.spraying() ? 0xFF9BE06A : 0xFFDDDDDD, true);
    }
}
