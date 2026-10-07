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
import com.chunkworks.rotorcraft.domain.Flight;
import com.chunkworks.rotorcraft.domain.Wear;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.Vec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Flying the box helicopter on a real server (its D-0001): it spools up before it climbs, then
 * climbs at its climb rate; let go, it holds its height; held down, it lands softly and nobody
 * aboard is hurt; out of fuel in the air it autorotates down, nobody hurt; a wall met at speed
 * wears it by the speed it carried, nobody hurt; worn to nothing in the air it comes down before
 * it is a wreck, nobody hurt; the pilot's reported moves are judged the same, once an impact;
 * Shift never drops a rider out and the get-out key works only near the ground; a low pass runs
 * nothing over; it settles on water and nobody goes under.
 */
@GameTestHolder(Rotorcraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlightGameTests {
    private static final int SIZE = 40;

    public FlightGameTests() {}

    /** The ride, sampled every tick: the rider's lowest health, the aircraft's sink the tick before it last stood, a trace. */
    private static final class Ride {
        float lowest;
        double lastSink;
        double fastestSink;
        boolean wasStanding = true;
        double touchdownSink;
        final StringBuilder trace = new StringBuilder();

        static Ride watch(GameTestHelper helper, Aircraft a, LivingEntity rider) {
            Ride ride = new Ride();
            ride.lowest = rider.getHealth();
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            int[] tick = {0};
            helper.onEachTick(() -> {
                ride.lowest = Math.min(ride.lowest, rider.getHealth());
                boolean standing = a.onGround();
                if (!ride.wasStanding && standing) {
                    ride.touchdownSink = ride.lastSink;
                }
                ride.wasStanding = standing;
                ride.lastSink = -a.flight().vy();
                if (!standing) {
                    ride.fastestSink = Math.max(ride.fastestSink, -a.flight().vy());
                }
                if (tick[0]++ % 10 == 0) {
                    ride.trace.append(String.format(Locale.ROOT, " t%d:y%.2f,vy%.2f,spool%.2f,hp%.0f,cond%d", tick[0],
                            a.getY() - origin.y, a.flight().vy(), a.rotor(), rider.getHealth(), a.condition()));
                }
            });
            return ride;
        }
    }

    @GameTest(template = "airfield", timeoutTicks = 300)
    public void aHullOfBoxesMeetsWhatOnlyItsBoomIsOver(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        // A pillar three high under the boom's end, 1.6 to 2.6 behind the mast: past the body's own
        // box (0.75) and its tail point (1.5), under the boom (0.5 to 2.5 back), so only the hull meets it.
        for (int y = Rigs.FLOOR; y < Rigs.FLOOR + 3; y++) {
            helper.setBlock(new BlockPos(20, y, 18), Blocks.STONE);
        }
        Aircraft a = Rigs.aircraft(helper, Rigs.BOX_BOOM, 20.5, Rigs.FLOOR + 4.5, 20.6, 0.0f);
        Rigs.crew(helper, a);
        a.setScriptedFlight(Rigs.fly(0, 0, -1));
        double pillarTop = helper.absoluteVec(new Vec3(0, Rigs.FLOOR + 3, 0)).y;
        helper.startSequence()
                .thenIdle(160)
                .thenExecute(() -> {
                    // The boom's underside is a block over the feet (16 px): it rests on the pillar.
                    double gap = a.getY() + 1.0 - pillarTop;
                    helper.assertTrue(gap > -0.05 && gap < 0.3, "the boom came down onto the pillar and stopped there: its underside " + gap + " over the top");
                    helper.assertTrue(!a.onGround(), "held up by its boom, short of the floor");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 40)
    public void itIsDrawnWhileOnlyABladeOrItsBoomIsInView(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.aircraft(helper, Rigs.BOX_BOOM, 20.5, Rigs.FLOOR, 20.5, 90.0f);
        AABB cull = a.getBoundingBoxForCulling().inflate(1e-6);
        // The main rotor turns four blocks out from its pivot, (0, 30, 4) px, whichever way it points.
        Vec3 pivot = a.position().add(a.rotate(new Vec(0.0, 30 / 16.0, 4 / 16.0)));
        for (int deg = 0; deg < 360; deg += 45) {
            Vec3 tip = pivot.add(4.0 * Math.cos(Math.toRadians(deg)), 0.0, 4.0 * Math.sin(Math.toRadians(deg)));
            helper.assertTrue(cull.contains(tip), "a blade's tip at " + deg + " degrees is inside the box it is drawn by: " + tip + " in " + cull);
        }
        Vec3 boomEnd = a.position().add(a.rotate(new Vec(0.0, 22 / 16.0, -40 / 16.0)));
        helper.assertTrue(cull.contains(boomEnd), "and the boom's end: " + boomEnd + " in " + cull);
        helper.succeed();
    }

    @GameTest(template = "airfield", timeoutTicks = 200)
    public void itSpoolsUpBeforeItClimbsThenClimbsAtItsClimbRate(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        Rigs.crew(helper, a);
        double y0 = a.getY();
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(Math.abs(a.getY() - y0) < 1e-6, "no climb while the rotor spools: at spool " + a.rotor() + ", y " + (a.getY() - y0));
            helper.assertTrue(a.rotor() > 0.4 && a.rotor() < Flight.LIFT, "spooling: " + a.rotor());
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(a.getY() > y0 + 4.0, "climbed: " + (a.getY() - y0));
            helper.assertValueEqual(a.flight().vy(), 0.4, "at its climb rate");
            helper.succeed();
        });
    }

    @GameTest(template = "airfield", timeoutTicks = 300)
    public void letGoItHoldsItsHeight(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        Rigs.crew(helper, a);
        double y0 = a.getY();
        double[] held = new double[1];
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getY() > y0 + 8.0, "climbing: " + (a.getY() - y0)))
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, 0)))
                .thenIdle(20)
                .thenExecute(() -> held[0] = a.getY())
                .thenExecuteFor(100, () -> helper.assertTrue(Math.abs(a.getY() - held[0]) <= 0.1,
                        "holding its height: off by " + (a.getY() - held[0])))
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 500)
    public void heldDownItLandsSoftlyAndNobodyAboardIsHurt(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 20.5, Rigs.FLOOR + 30, 20.5, 0.0f);
        LivingEntity rider = Rigs.crew(helper, a);
        float health = rider.getHealth();
        Ride ride = Ride.watch(helper, a, rider);
        a.setScriptedFlight(Rigs.fly(0, 0, -1));
        double floor = helper.absoluteVec(new Vec3(0, Rigs.FLOOR, 0)).y;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.onGround() && a.getY() < floor + 0.01, "down on the floor:" + ride.trace))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(ride.fastestSink > 0.4, "it came down at speed first: " + ride.fastestSink + ride.trace);
                    helper.assertTrue(ride.touchdownSink <= 0.2, "and met the floor softly: " + ride.touchdownSink + ride.trace);
                    helper.assertValueEqual(a.condition(), Wear.FULL_CONDITION, "a landing, not a crash:" + ride.trace);
                    helper.assertValueEqual(ride.lowest, health, "nobody aboard was hurt:" + ride.trace);
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 600)
    public void outOfFuelInTheAirItAutorotatesDownAndNobodyIsHurt(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        LivingEntity rider = Rigs.crew(helper, a);
        float health = rider.getHealth();
        a.setFuel(100);   // the spool-up and a climb of some twenty blocks, then nothing: the template is forty high, its ceiling a barrier
        Ride ride = Ride.watch(helper, a, rider);
        double y0 = a.getY();
        double[] top = new double[1];
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.tank().ticks() == 0, "the tank ran dry:" + ride.trace))
                .thenExecute(() -> {
                    top[0] = a.getY() - y0;
                    a.setScriptedFlight(Rigs.fly(0, 0, 0));
                })
                .thenWaitUntil(() -> helper.assertTrue(a.onGround() && a.getY() < y0 + 0.01, "down again:" + ride.trace))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(top[0] > 10.0, "it was high when the engine stopped: " + top[0]);
                    helper.assertTrue(Math.abs(ride.fastestSink - Flight.AUTOROTATION) < 0.01, "it came down autorotating: " + ride.fastestSink + ride.trace);
                    helper.assertValueEqual(a.condition(), Wear.FULL_CONDITION, "and landed, not crashed:" + ride.trace);
                    helper.assertValueEqual(ride.lowest, health, "nobody aboard was hurt:" + ride.trace);
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 400)
    public void aWallMetAtSpeedWearsItByTheSpeedItCarriedAndNobodyIsHurt(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        for (int y = Rigs.FLOOR; y < Rigs.FLOOR + 12; y++) {
            for (int z = 8; z < 33; z++) {
                helper.setBlock(new BlockPos(37, y, z), Blocks.STONE);
            }
        }
        Aircraft a = Rigs.heli(helper, 3.5, Rigs.FLOOR, 20.5, -90.0f);   // facing east, the wall ahead
        LivingEntity rider = Rigs.crew(helper, a);
        float health = rider.getHealth();
        Ride ride = Ride.watch(helper, a, rider);
        double y0 = a.getY();
        double wall = helper.absoluteVec(new Vec3(37, 0, 0)).x;
        double[] speed = new double[1];
        helper.onEachTick(() -> {
            if (a.condition() == Wear.FULL_CONDITION) {
                speed[0] = a.flight().speed();
            }
        });
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getY() > y0 + 3.0, "up off the ground"))
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(1, 0, 0)))
                .thenWaitUntil(() -> helper.assertTrue(a.condition() < Wear.FULL_CONDITION, "into the wall:" + ride.trace))
                .thenIdle(10)
                .thenExecute(() -> {
                    a.setScriptedFlight(null);
                    helper.assertTrue(Math.abs(speed[0] - 1.0) < 1e-9, "it was at its top speed when it met the wall: " + speed[0]);
                    helper.assertValueEqual(a.condition(), Wear.FULL_CONDITION - Wear.crash(1.0), "worn by a crash at its top speed, once:" + ride.trace);
                    helper.assertTrue(a.getX() < wall, "and stopped at the wall: " + (a.getX() - wall));
                    helper.assertValueEqual(ride.lowest, health, "nobody aboard was hurt");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 600)
    public void wornToNothingInTheAirItComesDownBeforeItIsAWreckAndNobodyIsHurt(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        LivingEntity rider = Rigs.crew(helper, a);
        float health = rider.getHealth();
        Ride ride = Ride.watch(helper, a, rider);
        double y0 = a.getY();
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getY() > y0 + 15.0, "climbing"))
                .thenExecute(() -> {
                    a.setScriptedFlight(Rigs.fly(0, 0, 0));
                    a.hurt(helper.getLevel().damageSources().generic(), 5.0f);   // all of it: 5 x 2000
                    helper.assertValueEqual(a.condition(), 0, "worn to nothing");
                    helper.assertTrue(!a.isRemoved(), "but not a wreck in the air");
                })
                .thenWaitUntil(() -> helper.assertTrue(a.isRemoved(), "a wreck once it came down:" + ride.trace))
                .thenExecute(() -> {
                    helper.assertTrue(ride.fastestSink <= Flight.AUTOROTATION + 1e-6, "it came down no faster than an autorotation: " + ride.fastestSink);
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, a.getBoundingBox().inflate(3.0)).size() == 1, "the packed wreck where it came down");
                    helper.assertTrue(rider.getVehicle() == null && rider.getY() < y0 + 1.0, "the rider is on the ground");
                    helper.assertValueEqual(rider.getHealth(), health, "unhurt");
                    helper.assertValueEqual(ride.lowest, health, "all the way down");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 60)
    public void thePilotsReportedMovesAreJudgedOnceAnImpactByTheSpeedCarried(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 8.5, Rigs.FLOOR + 10, 20.5, -90.0f);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        pilot.setPos(a.getX(), a.getY(), a.getZ());
        helper.assertTrue(pilot.startRiding(a, true), "a player at the controls: the server flies nothing itself");
        // Moves as the packet handler applies them, a tick of the client's flight each.
        a.move(MoverType.PLAYER, new Vec3(0.9, 0, 0));
        a.move(MoverType.PLAYER, new Vec3(0.9, 0, 0));
        double turn = Math.toRadians(4.0);
        a.move(MoverType.PLAYER, new Vec3(0.9 * Math.cos(turn), 0, 0.9 * Math.sin(turn)));
        helper.assertValueEqual(a.condition(), Wear.FULL_CONDITION, "a turn the flight can make is no crash");
        a.move(MoverType.PLAYER, new Vec3(0.9, 0, 0));
        a.move(MoverType.PLAYER, new Vec3(0.3, 0, 0));   // stopped a third of the way through the tick
        int once = Wear.FULL_CONDITION - Wear.crash(0.9);
        helper.assertValueEqual(a.condition(), once, "a crash at the speed it carried, 0.9");
        a.move(MoverType.PLAYER, Vec3.ZERO);              // the rest of the same impact
        helper.assertValueEqual(a.condition(), once, "charged once");
        a.move(MoverType.PLAYER, new Vec3(0.03, 0, 0));
        a.move(MoverType.PLAYER, Vec3.ZERO);              // pushing against the wall
        helper.assertValueEqual(a.condition(), once, "pushing against the wall is no crash");
        helper.succeed();
    }

    @GameTest(template = "airfield", timeoutTicks = 100)
    public void shiftNeverDropsARiderOutAndGettingOutWorksOnlyNearTheGround(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 20.5, Rigs.FLOOR + 10, 20.5, 0.0f);
        ServerPlayer rider = Rigs.player(helper, "rider", GameType.SURVIVAL, new Vec3(20.5, Rigs.FLOOR + 10, 20.5));
        helper.assertTrue(rider.startRiding(a, true), "aboard");
        rider.setShiftKeyDown(true);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(rider.getVehicle() == a, "Shift held, still aboard: it is the collective here");
                    helper.assertTrue(rider.getPose() != Pose.CROUCHING, "and the rider is not crouching in the seat");
                    rider.setShiftKeyDown(false);
                    a.getOut(rider);
                    helper.assertTrue(rider.getVehicle() == a, "ten blocks up, too high to get out");
                    Vec3 low = helper.absoluteVec(new Vec3(20.5, Rigs.FLOOR + 2.5, 20.5));
                    a.setPos(low.x, low.y, low.z);
                    a.getOut(rider);
                    helper.assertTrue(rider.getVehicle() == null, "two and a half blocks up, out");
                    Rigs.logOff(rider);
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 300)
    public void aLowPassRunsNothingOver(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 4.5, Rigs.FLOOR, 20.5, -90.0f);
        Rigs.crew(helper, a);
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "a cow");
        Vec3 at = helper.absoluteVec(new Vec3(24.5, Rigs.FLOOR, 20.5));
        cow.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        float health = cow.getHealth();
        double[] fastest = new double[1];
        helper.onEachTick(() -> fastest[0] = Math.max(fastest[0], a.flight().speed()));
        a.setScriptedFlight(Rigs.fly(1, 0, 0));   // lifted, hover-taxiing along the ground
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getX() > at.x + 5.0, "past the cow: " + (a.getX() - at.x)))
                .thenExecute(() -> {
                    helper.assertTrue(fastest[0] > 0.5, "at speed: " + fastest[0]);
                    helper.assertValueEqual(cow.getHealth(), health, "the cow under it is unhurt");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 500)
    public void itSettlesOnWaterAndNobodyGoesUnder(GameTestHelper helper) {
        // A pond three deep: stone under it and round it, its surface a little under y 4.
        int top = 4;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean pond = x >= 12 && x < 29 && z >= 12 && z < 29;
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y < top; y++) {
                    helper.setBlock(new BlockPos(x, y, z), pond ? Blocks.WATER : Blocks.STONE);
                }
            }
        }
        Aircraft a = Rigs.heli(helper, 20.5, top + 12, 20.5, 0.0f);
        LivingEntity rider = Rigs.crew(helper, a);
        a.setScriptedFlight(Rigs.fly(0, 0, -1));
        double surface = helper.absoluteVec(new Vec3(0, top - 1, 0)).y + helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(20, top - 1, 20)))
                .getHeight(helper.getLevel(), helper.absolutePos(new BlockPos(20, top - 1, 20)));
        int[] still = {0};
        StringBuilder trace = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            still[0] = Math.abs(a.flight().vy()) < 1e-6 ? still[0] + 1 : 0;
            if (tick[0]++ % 5 == 0 || rider.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
                trace.append(String.format(Locale.ROOT, " t%d:y%.2f,eye%.2f,air%d,riding%s", tick[0], a.getY() - surface, rider.getEyeY() - surface,
                        rider.getAirSupply(), rider.getVehicle() == a));
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(still[0] >= 20, "settled: y " + a.getY() + " over a surface at " + surface))
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(a.getY() - surface) < 0.05, "on the surface, not the pond's floor: " + (a.getY() - surface));
                    helper.assertValueEqual(rider.getAirSupply(), rider.getMaxAirSupply(), "the rider never went under:" + trace);
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 40)
    public void theBoxHelicopterIsAnAircraftAndTheBoxCrateASlungLoad(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Vehicle heli = Rigs.heli(helper, 10.5, Rigs.FLOOR, 10.5, 0.0f);
        Vehicle crate = Rigs.crate(helper, 25.5, Rigs.FLOOR, 25.5, 0.0f);
        helper.assertTrue(((Aircraft) heli).aircraft() != null && ((Aircraft) heli).hookPoint() != null, "its flight profile and hook are read");
        helper.assertTrue(((com.chunkworks.rotorcraft.SlungLoad) crate).sling() != null, "its sling profile is read");
        helper.assertTrue(heli.profile().wheels().parts() == 0, "its skids are no wheels to the lift");
        helper.succeed();
    }
}
