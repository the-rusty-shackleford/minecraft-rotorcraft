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
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.rotorcraft.domain.Flight;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The collective's dial (D-0005), while the local player flies an aircraft: a round housing on a
 * pedestal with the rotor turning inside it as the real one turns, and around it an arc lit from
 * its bottom-left end up to the lever, a white tick at twelve o'clock its detent. Built as Immersive
 * Aircraft's engine dial is and drawn where it draws it, centred over the hotbar between the hearts
 * and the hunger bar (Rusty, 2026-10-10: "I like the circular throttle bar from immersive aircraft
 * more"). Its frames are devtools/art/gauge.py's. Only the pilot's client knows the lever (it flies
 * the aircraft), so only the pilot sees it.
 */
public final class CollectiveGauge {
    private CollectiveGauge() {}

    private static final ResourceLocation SHEET = Rotorcraft.id("textures/gui/collective.png");
    /** The sheet's layout, as gauge.py draws it: frames of SIZE pixels, COLUMNS a row; LEVELS dial frames, then ROTOR rotor frames. */
    private static final int SIZE = 18, COLUMNS = 9, LEVELS = 41, ROTOR = 8, WIDTH = SIZE * COLUMNS, HEIGHT = SIZE * 6;
    /** The dial's centre over the screen's bottom, as Immersive Aircraft's; seven lower with no experience bar (creative). */
    private static final int RISE = 37, NO_XP_DROP = 7;

    /** effects: draws the dial if the local player flies an aircraft */
    public static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof Aircraft a)
                || a.airframe() == null || a.getControllingPassenger() != mc.player) {
            return;
        }
        double lever = a.flight().collective();
        // In the detent it holds the height: shown at the tick, wherever in the detent the lever is.
        int level = Flight.climbShare(lever, FlightFeel.controls()) == 0.0 ? (LEVELS - 1) / 2 : (int) Math.round(lever * (LEVELS - 1));
        // A frame a tick at full spool: the rotor's own angle advances by its spool times SPIN a tick.
        int blades = LEVELS + Math.floorMod((long) Math.floor(a.rotorAngle(delta.getGameTimeDeltaPartialTick(false)) / Aircraft.SPIN), ROTOR);
        int x = g.guiWidth() / 2 - SIZE / 2;
        int y = g.guiHeight() - RISE - SIZE / 2 + (mc.gameMode != null && !mc.gameMode.hasExperience() ? NO_XP_DROP : 0);
        RenderSystem.enableBlend();
        blitFrame(g, level, x, y);
        blitFrame(g, blades, x, y);
        RenderSystem.disableBlend();
    }

    private static void blitFrame(GuiGraphics g, int frame, int x, int y) {
        g.blit(SHEET, x, y, (frame % COLUMNS) * SIZE, (frame / COLUMNS) * SIZE, SIZE, SIZE, WIDTH, HEIGHT);
    }
}
