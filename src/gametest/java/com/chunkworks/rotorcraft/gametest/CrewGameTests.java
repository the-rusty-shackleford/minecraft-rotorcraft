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
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A crew across logging out of an aircraft in the air (Vanilla Wheels' D-0038), on real players
 * logged out and in through the player list. On 2026-10-11 OtatopMalloy and Bobandy_ timed out of a
 * hovering Huey a quarter of a second apart: the pilot was saved standing in the air, the Huey went
 * into Bobandy_'s save as the last one aboard, and the pilot logged back in fifty blocks over the
 * ground and died. Here, twenty-six up in the box helicopter, with nobody's client flying it (a
 * pilot's client that stops talking holds an aircraft where it is): a pilot who drops out leaves the
 * crew in their seats and nobody at the controls, so the aircraft settles with them unhurt, and the
 * pilot logs back in to their seat in it; one who logs back in to it gone is stood on the ground
 * under where they were, and the crew who took it along come back in it, and it settles with them.
 */
@GameTestHolder(Rotorcraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrewGameTests {
    private static final int SIZE = 40;
    /** Where the aircraft hangs: twenty-six blocks over the ground, a fall nobody survives. */
    private static final double HIGH = Rigs.FLOOR + 26.0;

    public CrewGameTests() {}

    /** effects: returns {@code profile} logged in through the player list, as the server logs in anyone: from their save */
    private static ServerPlayer logIn(GameTestHelper helper, GameProfile profile) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, sp, cookie);
        return sp;
    }

    /** effects: logs {@code sp} out as a timed-out connection does, and checks they are gone */
    private static void logOut(GameTestHelper helper, ServerPlayer sp) {
        sp.connection.disconnect(Component.literal("Timed out"));
        helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(sp.getUUID()) == null, sp.getScoreboardName() + " is logged out");
    }

    /** effects: returns how far apart {@code a} and {@code b} are across the ground */
    private static double across(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** effects: returns the ground's top in the level's heights */
    private static double ground(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0, Rigs.FLOOR, 0)).y;
    }

    /** effects: returns whether {@code a} has come to rest on the ground: within the hair it stands on ({@link Aircraft#standing}), sinking no more */
    private static boolean settled(GameTestHelper helper, Aircraft a) {
        return a.getY() - ground(helper) < 0.05 && Math.abs(a.flight().vy()) < 1e-6;
    }

    /** effects: tracks the lowest health {@code who} holds from now, whoever it holds each tick */
    private static float[] lowest(GameTestHelper helper, AtomicReference<ServerPlayer> who) {
        float[] low = {Float.MAX_VALUE};
        helper.onEachTick(() -> {
            ServerPlayer sp = who.get();
            if (sp != null && !sp.isRemoved()) {
                low[0] = Math.min(low[0], sp.getHealth());
            }
        });
        return low;
    }

    @GameTest(template = "airfield", timeoutTicks = 600)
    public void aPilotWhoDropsOutLeavesTheCrewInTheirSeatsAndTheAircraftSettlesWithThemThenTakesTheirSeatAgain(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft heli = Rigs.heli(helper, 20.5, HIGH, 20.5, 0.0f);
        ServerPlayer pilot = Rigs.player(helper, "heli-pilot", GameType.SURVIVAL, new Vec3(20.5, Rigs.FLOOR, 20.5));
        ServerPlayer crew = Rigs.player(helper, "heli-crew", GameType.SURVIVAL, new Vec3(20.5, Rigs.FLOOR, 20.5));
        GameProfile pilotProfile = pilot.getGameProfile();
        helper.assertTrue(pilot.startRiding(heli, true) && crew.startRiding(heli, true), "both aboard");
        helper.assertTrue(heli.getControllingPassenger() == pilot, "the pilot flies");
        AtomicReference<ServerPlayer> watched = new AtomicReference<>(crew);
        float[] crewLowest = lowest(helper, watched);
        AtomicReference<Vec3> crewSeat = new AtomicReference<>(), pilotSeat = new AtomicReference<>();
        AtomicReference<ServerPlayer> back = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(heli.getY() - helper.absoluteVec(new Vec3(0, HIGH, 0)).y) < 0.01, "held where it is while the pilot's client says nothing");
                    pilotSeat.set(pilot.position().subtract(heli.position()));
                    crewSeat.set(crew.position().subtract(heli.position()));
                    logOut(helper, pilot);
                    helper.assertTrue(!heli.isRemoved() && crew.getVehicle() == heli, "two aboard: it stays in the world with the crew");
                    helper.assertTrue(heli.getControllingPassenger() == null, "nobody is handed the controls: " + heli.getControllingPassenger());
                })
                .thenIdle(1)
                .thenExecute(() -> helper.assertTrue(across(crew.position().subtract(heli.position()), crewSeat.get()) < 0.05,
                        "the crew in their own seat, not moved into the pilot's"))
                .thenWaitUntil(() -> helper.assertTrue(settled(helper, heli), "nobody at the controls: the server flies it unpowered, down: "
                        + String.format(java.util.Locale.ROOT, "%.2f up, sinking %.3f, spool %.2f, at the controls %s", heli.getY() - ground(helper), -heli.flight().vy(), heli.flight().spool(), heli.getControllingPassenger())))
                .thenExecute(() -> {
                    helper.assertTrue(crewLowest[0] == crew.getMaxHealth(), "the crew came down unhurt: " + crewLowest[0]);
                    ServerPlayer p = logIn(helper, pilotProfile);
                    back.set(p);
                    helper.assertTrue(p.getVehicle() == heli, "the pilot logged back in aboard it, on the ground now: " + p.getVehicle() + " at " + p.position());
                    helper.assertTrue(heli.getControllingPassenger() == p, "in their own seat, at the controls");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    ServerPlayer p = back.get();
                    helper.assertTrue(across(p.position().subtract(heli.position()), pilotSeat.get()) < 0.05, "in the pilot's seat");
                    helper.assertTrue(p.getHealth() == p.getMaxHealth(), "unhurt: " + p.getHealth());
                    logOut(helper, p);
                    logOut(helper, crew);
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 600)
    public void aPilotWhoLogsInToTheAircraftGoneWithTheCrewIsStoodOnTheGroundAndTheCrewComesBackInIt(GameTestHelper helper) {
        // OtatopMalloy's death: the pilot drops, the crew a quarter of a second later and takes the aircraft along.
        Rigs.floor(helper, SIZE);
        Aircraft heli = Rigs.heli(helper, 20.5, HIGH, 20.5, 0.0f);
        ServerPlayer pilot = Rigs.player(helper, "gone-pilot", GameType.SURVIVAL, new Vec3(20.5, Rigs.FLOOR, 20.5));
        ServerPlayer crew = Rigs.player(helper, "gone-crew", GameType.SURVIVAL, new Vec3(20.5, Rigs.FLOOR, 20.5));
        GameProfile pilotProfile = pilot.getGameProfile(), crewProfile = crew.getGameProfile();
        helper.assertTrue(pilot.startRiding(heli, true) && crew.startRiding(heli, true), "both aboard");
        AtomicReference<ServerPlayer> pilotBack = new AtomicReference<>(), crewBack = new AtomicReference<>();
        float[] pilotLowest = lowest(helper, pilotBack), crewLowest = lowest(helper, crewBack);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> logOut(helper, pilot))
                .thenIdle(5)
                .thenExecute(() -> {
                    logOut(helper, crew);
                    helper.assertTrue(heli.getRemovalReason() == Entity.RemovalReason.UNLOADED_WITH_PLAYER, "the last one aboard took it along, as the game does");
                    ServerPlayer p = logIn(helper, pilotProfile);
                    pilotBack.set(p);
                    helper.assertTrue(p.getVehicle() == null, "nothing to sit in");
                    helper.assertTrue(Math.abs(p.getY() - ground(helper)) < 1e-6, "stood on the ground, not " + (p.getY() - ground(helper)) + " blocks over it");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(pilotLowest[0] == pilotBack.get().getMaxHealth(), "the pilot unhurt: " + pilotLowest[0]);
                    ServerPlayer c = logIn(helper, crewProfile);
                    crewBack.set(c);
                    helper.assertTrue(c.getVehicle() instanceof Aircraft, "the crew comes back in the aircraft they took along");
                    helper.assertTrue(c.getVehicle().getY() > ground(helper) + 20.0, "up where it was");
                    helper.assertTrue(((Aircraft) c.getVehicle()).getControllingPassenger() == null, "with nobody at the controls: the crew is in their own seat");
                })
                .thenWaitUntil(() -> helper.assertTrue(crewBack.get().getVehicle() instanceof Aircraft a && settled(helper, a), "it settles"))
                .thenExecute(() -> {
                    helper.assertTrue(crewLowest[0] == crewBack.get().getMaxHealth(), "the crew came down in it unhurt: " + crewLowest[0]);
                    logOut(helper, crewBack.get());
                    logOut(helper, pilotBack.get());
                })
                .thenSucceed();
    }
}
