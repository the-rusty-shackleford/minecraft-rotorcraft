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

import com.chunkworks.rotorcraft.Aircraft;
import com.chunkworks.rotorcraft.SlungLoad;
import com.chunkworks.rotorcraft.domain.FlightInput;
import com.chunkworks.vanillawheels.Vehicle;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** What the tests build and fly with: the ground, the box helicopter and crate, the crews. */
final class Rigs {
    private Rigs() {}

    static final ResourceLocation BOX_HELI = ResourceLocation.fromNamespaceAndPath(GameTestMod.MOD_ID, "box_heli");
    static final ResourceLocation BOX_CRATE = ResourceLocation.fromNamespaceAndPath(GameTestMod.MOD_ID, "box_crate");
    /** The box helicopter with its body three blocks long and a hull of boxes, its boom reaching past the body's tail. */
    static final ResourceLocation BOX_BOOM = ResourceLocation.fromNamespaceAndPath(GameTestMod.MOD_ID, "box_boom");
    /** The ground's top, in the test's own coordinates. */
    static final int FLOOR = 2;

    /** effects: lays stone {@link #FLOOR} deep over the template's first {@code size} by {@code size} blocks */
    static void floor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y < FLOOR; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /** effects: returns a fuelled box helicopter at (x, y, z) facing {@code yaw}, in the level */
    static Aircraft heli(GameTestHelper helper, double x, double y, double z, float yaw) {
        return aircraft(helper, BOX_HELI, x, y, z, yaw);
    }

    /** effects: returns a fuelled aircraft of profile {@code id} at (x, y, z) facing {@code yaw}, in the level */
    static Aircraft aircraft(GameTestHelper helper, ResourceLocation id, double x, double y, double z, float yaw) {
        Vehicle v = Vehicle.create(helper.getLevel(), id, helper.absoluteVec(new Vec3(x, y, z)), yaw);
        helper.assertTrue(v instanceof Aircraft, id + " is an aircraft: " + v);
        v.setFuel(v.tank().capacity());
        helper.getLevel().addFreshEntity(v);
        return (Aircraft) v;
    }

    /** effects: returns a box crate at (x, y, z) facing {@code yaw}, in the level */
    static SlungLoad crate(GameTestHelper helper, double x, double y, double z, float yaw) {
        Vehicle v = Vehicle.create(helper.getLevel(), BOX_CRATE, helper.absoluteVec(new Vec3(x, y, z)), yaw);
        helper.assertTrue(v instanceof SlungLoad, "the box crate is a slung load: " + v);
        helper.getLevel().addFreshEntity(v);
        return (SlungLoad) v;
    }

    /**
     * effects: returns a villager with no AI in {@code a}'s second seat, behind an armor stand at the
     * controls -- a pilot that is no player, so the server flies by the script (Vanilla Wheels'
     * D-0018 rig). A villager with no AI never heals, so what it loses is the damage.
     */
    static LivingEntity crew(GameTestHelper helper, Aircraft a) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
        Villager rider = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(stand != null && rider != null, "an armor stand and a villager");
        for (LivingEntity e : List.of(stand, rider)) {
            e.setPos(a.getX(), a.getY(), a.getZ());
            helper.getLevel().addFreshEntity(e);
        }
        rider.setNoAi(true);
        helper.assertTrue(stand.startRiding(a, true), "the stand-in takes the controls");
        helper.assertTrue(rider.startRiding(a, true), "the villager takes a seat");
        helper.assertTrue(a.getControllingPassenger() == stand, "the stand-in flies, so the server does");
        return rider;
    }

    /** effects: returns the script's input: the stick, the pedal and the collective, its power and ground filled in by the truth each tick */
    static FlightInput fly(int forward, int turn, int lift) {
        return new FlightInput(forward, turn, lift, true, false, 0.0);
    }

    /** A server player with a connection that goes nowhere, so key handlers, menus and messages take the real path. */
    static ServerPlayer player(GameTestHelper helper, String name, GameType mode, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, sp, cookie);
        sp.setGameMode(mode);
        Vec3 abs = helper.absoluteVec(at);
        sp.teleportTo(abs.x, abs.y, abs.z);
        return sp;
    }

    /** effects: logs {@code sp} off, as a test's last act */
    static void logOff(ServerPlayer sp) {
        sp.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
    }
}
