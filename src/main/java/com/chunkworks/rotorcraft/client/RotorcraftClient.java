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
import com.chunkworks.rotorcraft.RotorcraftContent;
import com.chunkworks.rotorcraft.api.Rotorcraft;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** The client's registrations: the two renderers, the keys, the sprayer's readout and hiss, the key ticks. */
@EventBusSubscriber(modid = Rotorcraft.MOD_ID, value = Dist.CLIENT)
public final class RotorcraftClient {
    private RotorcraftClient() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(FlightControls::onClientTick);
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide() && e.getEntity() instanceof Aircraft aircraft) {
                Minecraft.getInstance().getSoundManager().play(new SprayerSound(aircraft));
            }
        });
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RotorcraftContent.AIRCRAFT.get(), AircraftRenderer::new);
        event.registerEntityRenderer(RotorcraftContent.SLUNG_LOAD.get(), SlungLoadRenderer::new);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(RotorcraftKeys.ASCEND);
        event.register(RotorcraftKeys.DESCEND);
        event.register(RotorcraftKeys.GET_OUT);
        event.register(RotorcraftKeys.HOOK);
        event.register(RotorcraftKeys.SPRAY);
    }

    @SubscribeEvent
    public static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Rotorcraft.id("sprayer"), SprayerIndicator::draw);
    }
}
