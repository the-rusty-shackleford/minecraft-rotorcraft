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
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.rotorcraft.domain.Sling;
import com.chunkworks.rotorcraft.domain.Wear;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The sling on a real server (D-0002 here): the hook takes a load whose eye is in reach and
 * nothing else; a load with a cow behind its doors and apples in its chest is hooked, lifted,
 * carried twenty blocks on its rope, set down softly and let go, the hook refusing to let go while
 * it hangs, and nothing aboard hurt or lost; a load snagged on a wall lets go and is worn; the hook
 * survives a save; a load set down on water rests on the surface and nothing aboard goes under.
 */
@GameTestHolder(Rotorcraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SlingGameTests {
    private static final int SIZE = 40;

    public SlingGameTests() {}

    /** effects: returns where {@code a} should stand for its hook to be {@code above} blocks over {@code load}'s eye */
    private static Vec3 hookOver(Aircraft a, SlungLoad load, double above) {
        Vec3 hook = a.hookPoint();
        Vec3 eye = load.eyePoint();
        return a.position().add(eye.x - hook.x, eye.y + above - hook.y, eye.z - hook.z);
    }

    @GameTest(template = "airfield", timeoutTicks = 40)
    public void theHookTakesALoadWhoseEyeIsInReachAndNothingElse(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        SlungLoad crate = Rigs.crate(helper, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        Aircraft a = Rigs.heli(helper, 10.5, Rigs.FLOOR + 10, 10.5, 0.0f);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 beside = hookOver(a, crate, 2.0).add(Sling.REACH + 0.5, 0.0, 0.0);
        a.setPos(beside.x, beside.y, beside.z);
        a.hookKey(pilot);
        helper.assertTrue(a.trailer() == null, "beside it, out of reach: nothing hooked");
        Vec3 high = hookOver(a, crate, 3.0 + Sling.REACH + 0.5);
        a.setPos(high.x, high.y, high.z);
        a.hookKey(pilot);
        helper.assertTrue(a.trailer() == null, "too far above it for the rope: nothing hooked");
        Vec3 over = hookOver(a, crate, 2.0);
        a.setPos(over.x, over.y, over.z);
        a.hookKey(pilot);
        helper.assertTrue(a.trailer() == crate && crate.tower() == a, "over it within the rope: hooked");
        helper.succeed();
    }

    @GameTest(template = "airfield", timeoutTicks = 1400)
    public void aLoadIsHookedCarriedOnItsRopeSetDownSoftlyAndLetGoWithNothingAboardHurtOrLost(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.heli(helper, 4.5, Rigs.FLOOR, 20.5, -90.0f);   // facing east
        Rigs.crew(helper, a);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        SlungLoad[] crate = new SlungLoad[1];
        Cow[] cow = new Cow[1];
        float[] health = new float[1];
        double[] start = new double[1];
        double y0 = a.getY();
        double rope = 3.0;
        double[] stretched = new double[1];
        double[] sink = new double[2];   // the crate's sink last tick, and when it came to rest
        double[] falling = new double[1];   // the most fall the game counted on the crate while it hung
        boolean[] resting = {true};
        StringBuilder trace = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (crate[0] != null && crate[0].tower() == a) {
                stretched[0] = Math.max(stretched[0], crate[0].eyePoint().distanceTo(a.hookPoint()));
                boolean now = crate[0].resting();
                if (now && !resting[0]) {
                    sink[1] = sink[0];
                }
                resting[0] = now;
                sink[0] = crate[0].yo - crate[0].getY();
                falling[0] = Math.max(falling[0], crate[0].fallDistance);
            }
            if (crate[0] != null && tick[0]++ % 20 == 0) {
                trace.append(String.format(Locale.ROOT, " t%d:heli(%.1f,%.1f),crate(%.1f,%.1f),rope%.2f,cond%d", tick[0], a.getX(), a.getY() - y0,
                        crate[0].getX(), crate[0].getY() - y0, crate[0].eyePoint().distanceTo(a.hookPoint()), crate[0].condition()));
            }
        });
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getY() > y0 + 1.5, "up"))   // it carries on a block and a half as the climb dies: about three up, the rope's reach over a load
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, 0)))
                .thenIdle(30)
                .thenExecute(() -> {
                    // A crate with a cow behind its doors and apples in its chest, set under the hovering hook.
                    Vec3 hook = a.hookPoint();
                    Vec3 at = helper.relativeVec(hook);
                    crate[0] = Rigs.crate(helper, at.x, Rigs.FLOOR, at.z, -90.0f);
                    crate[0].toggleDoors();
                    cow[0] = EntityType.COW.create(helper.getLevel());
                    cow[0].moveTo(crate[0].getX(), crate[0].getY(), crate[0].getZ(), 0.0f, 0.0f);
                    cow[0].setNoAi(true);
                    helper.getLevel().addFreshEntity(cow[0]);
                    helper.assertTrue(cow[0].startRiding(crate[0], true), "the cow boards through the open doors");
                    health[0] = cow[0].getHealth();
                    crate[0].setItem(0, new ItemStack(Items.APPLE, 10));
                    a.hookKey(pilot);
                    helper.assertTrue(a.trailer() == crate[0], "hooked from the hover");
                    start[0] = crate[0].getX();
                    a.setScriptedFlight(Rigs.fly(0, 0, 1));
                })
                .thenWaitUntil(() -> helper.assertTrue(crate[0].getY() > y0 + 3.0, "the crate lifted off:" + trace))
                .thenExecute(() -> {
                    a.hookKey(pilot);
                    helper.assertTrue(a.trailer() == crate[0], "the hook will not let go of a hanging load");
                    a.setScriptedFlight(Rigs.fly(1, 0, 0));
                })
                .thenWaitUntil(() -> helper.assertTrue(crate[0].getX() > start[0] + 10.0, "carried east:" + trace))
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, 0)))
                .thenIdle(60)
                // From high enough that, held down, the helicopter reaches its full descent with the crate still far up.
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, 1)))
                .thenWaitUntil(() -> helper.assertTrue(crate[0].getY() > y0 + 10.0, "up again:" + trace))
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, 0)))
                .thenIdle(30)
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, -1)))
                .thenWaitUntil(() -> helper.assertTrue(crate[0].resting(), "set down:" + trace))
                // Still held down with the load resting on the hook: the hook comes softly down onto the
                // load's eye and stops a quarter block over it, the aircraft never sinking into its load.
                .thenExecuteFor(80, () -> helper.assertTrue(a.hookPoint().y >= crate[0].eyePoint().y + Aircraft.HOOK_CLEAR - 0.02,
                        "the hook stays over the resting load's eye: " + (a.hookPoint().y - crate[0].eyePoint().y) + trace))
                .thenExecute(() -> {
                    helper.assertTrue(a.hookPoint().y < crate[0].eyePoint().y + Aircraft.HOOK_CLEAR + 0.1,
                            "and came down onto it: " + (a.hookPoint().y - crate[0].eyePoint().y) + trace);
                    helper.assertValueEqual(a.condition(), Wear.FULL_CONDITION, "softly:" + trace);
                    a.setScriptedFlight(Rigs.fly(0, 0, 0));
                    a.hookKey(pilot);
                    helper.assertTrue(a.trailer() == null && crate[0].tower() == null, "let go once it rests");
                    helper.assertTrue(crate[0].getX() > start[0] + 15.0, "carried some way: " + (crate[0].getX() - start[0]));
                    helper.assertTrue(stretched[0] <= rope + 0.6, "never far past its rope while hooked: " + stretched[0]);
                    helper.assertTrue(sink[1] <= Wear.TOUCHDOWN_SAFE, "set down softly: it met the floor sinking " + sink[1] + trace);
                    helper.assertValueEqual(falling[0], 0.0, "on its rope it never counted as falling");
                    helper.assertValueEqual(crate[0].condition(), Wear.FULL_CONDITION, "unworn:" + trace);
                    helper.assertTrue(crate[0].cargoAboard().contains(cow[0]), "the cow is still aboard");
                    helper.assertValueEqual(cow[0].getHealth(), health[0], "and unhurt");
                    helper.assertValueEqual(crate[0].getItem(0).getCount(), 10, "the apples are still in the chest");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 300)
    public void aLoadCaughtUnderARoofLetsGoAndIsWorn(GameTestHelper helper) {
        // A load against a wall's face is pulled up it and over, the rope being short and the face
        // smooth; one under a roof cannot be. A crate under a roof half a block over it, the
        // helicopter on the roof with its hook over the crate's eye.
        Rigs.floor(helper, 40);
        int roof = Rigs.FLOOR + 2;
        for (int x = 14; x < 27; x++) {
            for (int z = 14; z < 27; z++) {
                helper.setBlock(new BlockPos(x, roof, z), Blocks.STONE);
            }
        }
        SlungLoad crate = Rigs.crate(helper, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        Aircraft a = Rigs.heli(helper, 20.5, roof + 1, 20.5, 0.0f);
        Vec3 over = hookOver(a, crate, 0.0);
        a.setPos(over.x, a.getY(), over.z);
        Rigs.crew(helper, a);
        a.hookKey(helper.makeMockPlayer(GameType.SURVIVAL));
        helper.assertTrue(a.trailer() == crate, "hooked through the roof");
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(crate.tower() == null, "caught under the roof, it let go"))
                .thenIdle(20)
                .thenExecute(() -> {
                    a.setScriptedFlight(null);
                    helper.assertTrue(crate.condition() < Wear.FULL_CONDITION, "and was worn: " + crate.condition());
                    helper.assertTrue(crate.getY() < helper.absoluteVec(new Vec3(0, roof, 0)).y, "still under the roof");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 40)
    public void theHookSurvivesASave(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        SlungLoad crate = Rigs.crate(helper, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        Aircraft a = Rigs.heli(helper, 10.5, Rigs.FLOOR + 10, 10.5, 0.0f);
        Vec3 over = hookOver(a, crate, 2.0);
        a.setPos(over.x, over.y, over.z);
        a.hookKey(helper.makeMockPlayer(GameType.SURVIVAL));
        helper.assertTrue(a.trailer() == crate, "hooked");
        CompoundTag load = crate.saveWithoutId(new CompoundTag());
        CompoundTag aircraft = a.saveWithoutId(new CompoundTag());
        helper.assertTrue(load.hasUUID("Tower") && load.getUUID("Tower").equals(a.getUUID()), "the load keeps its aircraft by UUID");
        helper.assertTrue(aircraft.hasUUID("Trailer") && aircraft.getUUID("Trailer").equals(crate.getUUID()), "the aircraft keeps its load by UUID");
        helper.succeed();
    }

    @GameTest(template = "airfield", timeoutTicks = 1400)
    public void aLoadSetDownOnWaterRestsOnTheSurfaceAndNothingAboardGoesUnder(GameTestHelper helper) {
        int top = 4;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean pond = x >= 16 && x < 39 && z >= 10 && z < 31;
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y < top; y++) {
                    helper.setBlock(new BlockPos(x, y, z), pond ? Blocks.WATER : Blocks.STONE);
                }
            }
        }
        Aircraft a = Rigs.heli(helper, 4.5, top, 20.5, -90.0f);
        Rigs.crew(helper, a);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        SlungLoad[] crate = new SlungLoad[1];
        Cow[] cow = new Cow[1];
        double y0 = a.getY();
        double surface = helper.absoluteVec(new Vec3(0, top - 1, 0)).y + helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(28, top - 1, 20)))
                .getHeight(helper.getLevel(), helper.absolutePos(new BlockPos(28, top - 1, 20)));
        a.setScriptedFlight(Rigs.fly(0, 0, 1));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.getY() > y0 + 1.5, "up"))   // it carries on a block and a half as the climb dies: about three up, the rope's reach over a load
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, 0)))
                .thenIdle(30)
                .thenExecute(() -> {
                    Vec3 at = helper.relativeVec(a.hookPoint());
                    crate[0] = Rigs.crate(helper, at.x, top, at.z, -90.0f);
                    crate[0].toggleDoors();
                    cow[0] = EntityType.COW.create(helper.getLevel());
                    cow[0].moveTo(crate[0].getX(), crate[0].getY(), crate[0].getZ(), 0.0f, 0.0f);
                    cow[0].setNoAi(true);
                    helper.getLevel().addFreshEntity(cow[0]);
                    helper.assertTrue(cow[0].startRiding(crate[0], true), "the cow boards");
                    a.hookKey(pilot);
                    a.setScriptedFlight(Rigs.fly(0, 0, 1));
                })
                .thenWaitUntil(() -> helper.assertTrue(crate[0].getY() > y0 + 3.0, "lifted"))
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(1, 0, 0)))
                .thenWaitUntil(() -> helper.assertTrue(crate[0].getX() > helper.absoluteVec(new Vec3(15.0, 0, 0)).x, "heading over the pond"))
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, 0)))
                .thenIdle(60)
                .thenExecute(() -> a.setScriptedFlight(Rigs.fly(0, 0, -1)))
                .thenWaitUntil(() -> helper.assertTrue(crate[0].resting(), "set down on the water"))
                .thenExecute(() -> {
                    a.setScriptedFlight(Rigs.fly(0, 0, 0));
                    a.hookKey(pilot);
                    helper.assertTrue(crate[0].tower() == null, "let go on the water");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(crate[0].getY() - surface) < 0.1, "resting on the surface, not the pond's floor: " + (crate[0].getY() - surface));
                    helper.assertValueEqual(crate[0].condition(), Wear.FULL_CONDITION, "set down on the water softly, unworn");
                    helper.assertValueEqual(cow[0].getAirSupply(), cow[0].getMaxAirSupply(), "the cow never went under");
                })
                .thenSucceed();
    }
}
