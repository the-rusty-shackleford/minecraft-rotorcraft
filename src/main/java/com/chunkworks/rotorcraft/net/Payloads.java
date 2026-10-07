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
package com.chunkworks.rotorcraft.net;

import com.chunkworks.rotorcraft.Aircraft;
import com.chunkworks.rotorcraft.api.Rotorcraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * What a client tells the server beyond Vanilla Wheels' own payloads (the speed, the pose): a key
 * pressed aboard an aircraft. Get out, from any seat; the hook and the sprayer, from the pilot's.
 * Each names the aircraft and is ignored unless the sender rides it (and, for the pilot's keys,
 * flies it). Sent on the press, never per tick.
 */
public final class Payloads {
    private Payloads() {}

    /** Bumped when a payload's shape changes; a mismatch refuses the connection early. */
    private static final String VERSION = "1";

    /** The get-out key, from any seat. */
    public record GetOut(int aircraft) implements CustomPacketPayload {
        public static final Type<GetOut> TYPE = new Type<>(Rotorcraft.id("get_out"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GetOut> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GetOut::aircraft, GetOut::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The hook key, from the pilot's seat. */
    public record Hook(int aircraft) implements CustomPacketPayload {
        public static final Type<Hook> TYPE = new Type<>(Rotorcraft.id("hook"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hook> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hook::aircraft, Hook::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The sprayer key, from the pilot's seat. */
    public record Spray(int aircraft) implements CustomPacketPayload {
        public static final Type<Spray> TYPE = new Type<>(Rotorcraft.id("spray"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Spray> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Spray::aircraft, Spray::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(GetOut.TYPE, GetOut.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level().getEntity(payload.aircraft()) instanceof Aircraft a && player.getVehicle() == a) {
                a.getOut(player);
            }
        });
        registrar.playToServer(Hook.TYPE, Hook.STREAM_CODEC, (payload, context) -> {
            Aircraft a = flown(context, payload.aircraft());
            if (a != null) {
                a.hookKey(context.player());
            }
        });
        registrar.playToServer(Spray.TYPE, Spray.STREAM_CODEC, (payload, context) -> {
            Aircraft a = flown(context, payload.aircraft());
            if (a != null) {
                a.sprayKey(context.player());
            }
        });
    }

    /** effects: returns the aircraft {@code id} names if the sender is a player flying it, else null, so a stray packet does nothing */
    private static Aircraft flown(IPayloadContext context, int id) {
        return context.player() instanceof ServerPlayer player && player.level().getEntity(id) instanceof Aircraft a
                && a.getControllingPassenger() == player ? a : null;
    }
}
