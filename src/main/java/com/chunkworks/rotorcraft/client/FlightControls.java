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
import com.chunkworks.rotorcraft.domain.Flight;
import com.chunkworks.rotorcraft.domain.FlightInput;
import com.chunkworks.rotorcraft.net.Payloads;
import com.chunkworks.vanillawheels.client.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The pilot's side of flying: the local player's keys as a {@link FlightInput} for the aircraft
 * they fly, the speed told to the server on a change (Vanilla Wheels' drive-state payload, so the
 * gauges and the engine note follow it everywhere), and the aircraft's keys sent as they are
 * pressed: the hook and the sprayer, from the pilot's seat. Up, Down and Get out are Vanilla Wheels'
 * (its D-0031).
 */
public final class FlightControls {
    private FlightControls() {}

    private static float lastSpeed = Float.NaN;
    private static int lastForward;

    /** effects: returns what the local player does at the controls of {@code aircraft}, with the world's facts; hands off if they do not fly it */
    public static FlightInput input(Aircraft aircraft, boolean powered, boolean onGround, double clearance) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || aircraft.getControllingPassenger() != player) {
            return new FlightInput(0, 0, 0, powered, onGround, clearance);
        }
        int forward = player.input.up ? 1 : player.input.down ? -1 : 0;
        int turn = player.input.right ? 1 : player.input.left ? -1 : 0;
        int lift = Keys.lift();
        return new FlightInput(forward, turn, lift, powered, onGround, clearance);
    }

    /** effects: tells the server the speed when it changed, as a car's client does */
    public static void report(Aircraft aircraft, Flight flight, FlightInput in) {
        float speed = (float) flight.speed();
        if (Float.isNaN(lastSpeed) || Math.abs(speed - lastSpeed) > 0.004f || in.forward() != lastForward) {
            lastSpeed = speed;
            lastForward = in.forward();
            PacketDistributor.sendToServer(new com.chunkworks.vanillawheels.net.Payloads.DriveState(aircraft.getId(), speed, 0.0f, in.forward(), false, 0.0f));
        }
    }

    /** The hook and the sprayer act once a press, not on the keyboard's repeats (Vanilla Wheels' D-0032). */
    private static final com.chunkworks.vanillawheels.client.Keys.Press HOOK_PRESS = new com.chunkworks.vanillawheels.client.Keys.Press(RotorcraftKeys.HOOK);
    private static final com.chunkworks.vanillawheels.client.Keys.Press SPRAY_PRESS = new com.chunkworks.vanillawheels.client.Keys.Press(RotorcraftKeys.SPRAY);

    /** The aircraft's keys, each tick: sent while aboard, swallowed otherwise. */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Aircraft aboard = mc.player != null && mc.player.getVehicle() instanceof Aircraft a ? a : null;
        boolean flying = aboard != null && aboard.getControllingPassenger() == mc.player;
        if (aboard == null) {
            lastSpeed = Float.NaN;
            RotorcraftKeys.releaseAll();
        }
        if (HOOK_PRESS.consume() && flying) {
            PacketDistributor.sendToServer(new Payloads.Hook(aboard.getId()));
        }
        if (SPRAY_PRESS.consume() && flying) {
            PacketDistributor.sendToServer(new Payloads.Spray(aboard.getId()));
        }
    }
}
