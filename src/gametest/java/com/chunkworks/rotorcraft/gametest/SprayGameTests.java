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
import com.chunkworks.rotorcraft.RotorcraftContent;
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.vanillawheels.Vehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The crop sprayer on a real server (D-0003 here), over a sixteen-by-sixteen plot of wheat just
 * sown, a grass border round it. A player flies (a mock one, moved as the packet handler moves
 * an aircraft): a pass six blocks up at a Huey's top speed, the boom past more than a column a
 * tick, doses every crop under the boom once, one bone meal a dose,
 * and touches nothing beside it nor the grass; a hover doses a column once until its cooldown has
 * passed; from twenty blocks up nothing; a bone meal event a claim mod cancels spends nothing; an
 * empty tank switches it off; packing keeps the sprayer and its bone meal; a creative pilot sprays
 * for free; the sprayer is fitted and taken off by hand, its bone meal handed back.
 */
@GameTestHolder(Rotorcraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SprayGameTests {
    /** The plot: wheat on farmland from LO to HI inclusive both ways, its crops at y CROP. */
    private static final int LO = 4, HI = 19, CROP = 2;
    /** The boom's swath at x 11.5 facing south: the box helicopter's is 7 across, columns 8 to 15. */
    private static final int SWATH_LO = 8, SWATH_HI = 15;
    private static final double X = 11.5;

    public SprayGameTests() {}

    /** effects: lays the plot: stone, then farmland with wheat at age 0 inside, grass round it */
    private static void plot(GameTestHelper helper) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean field = x >= LO && x <= HI && z >= LO && z <= HI;
                helper.setBlock(new BlockPos(x, 1, z), field ? Blocks.FARMLAND : Blocks.GRASS_BLOCK);
                if (field) {
                    helper.setBlock(new BlockPos(x, CROP, z), Blocks.WHEAT.defaultBlockState());
                }
            }
        }
    }

    /** effects: returns the box helicopter over the plot at (X, CROP + height, z) facing south, flown by {@code pilot}, a crop sprayer fitted with {@code meal} bone meal in it */
    private static Aircraft sprayer(GameTestHelper helper, Player pilot, double height, double z, int meal) {
        Aircraft a = Rigs.heli(helper, X, CROP + height, z, 0.0f);
        pilot.setPos(a.getX(), a.getY(), a.getZ());
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RotorcraftContent.CROP_SPRAYER.get()));
        helper.assertTrue(a.interact(pilot, InteractionHand.MAIN_HAND).consumesAction(), "the sprayer is fitted by hand");
        helper.assertTrue(a.sprayerFitted(), "fitted");
        if (meal > 0) {
            pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL, meal));
            a.interact(pilot, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(a.bonemeal(), meal, "loaded");
        }
        pilot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(pilot.startRiding(a, true), "the pilot boards");
        return a;
    }

    private static int age(GameTestHelper helper, int x, int z) {
        BlockState s = helper.getBlockState(new BlockPos(x, CROP, z));
        return s.getBlock() instanceof CropBlock crop ? crop.getAge(s) : -1;
    }

    /** effects: returns how many crops in the plot have grown from age 0 */
    private static int grown(GameTestHelper helper) {
        int n = 0;
        for (int x = LO; x <= HI; x++) {
            for (int z = LO; z <= HI; z++) {
                if (age(helper, x, z) > 0) {
                    n++;
                }
            }
        }
        return n;
    }

    /** effects: has the pilot fly {@code a} south from where it is by {@code step} a tick, {@code ticks} ticks, as the packet handler applies a client's moves */
    private static void pass(GameTestHelper helper, Aircraft a, double step, int ticks, Runnable after) {
        helper.startSequence()
                .thenExecuteFor(ticks, () -> a.move(MoverType.PLAYER, new Vec3(0, 0, step)))
                .thenIdle(2)
                .thenExecute(after)
                .thenSucceed();
    }

    @GameTest(template = "farm", timeoutTicks = 200)
    public void aPassDosesEveryCropUnderTheBoomOnceABoneMealAndNothingElse(GameTestHelper helper) {
        plot(helper);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Aircraft a = sprayer(helper, pilot, 6.0, 0.5, 192);
        a.sprayKey(pilot);
        helper.assertTrue(a.spraying(), "spraying");
        pass(helper, a, 1.4, 17, () -> {
            for (int x = LO; x <= HI; x++) {
                for (int z = LO; z <= HI; z++) {
                    boolean under = x >= SWATH_LO && x <= SWATH_HI;
                    int age = age(helper, x, z);
                    helper.assertTrue(under ? age > 0 : age == 0, (under ? "dosed" : "untouched") + " at " + x + "," + z + ": age " + age);
                }
            }
            for (int x = 0; x < 24; x++) {
                for (int z = 0; z < 24; z++) {
                    boolean field = x >= LO && x <= HI && z >= LO && z <= HI;
                    if (!field) {
                        helper.assertTrue(helper.getBlockState(new BlockPos(x, CROP, z)).isAir(), "nothing grew on the grass at " + x + "," + z);
                    }
                }
            }
            int dosed = (SWATH_HI - SWATH_LO + 1) * (HI - LO + 1);
            helper.assertValueEqual(grown(helper), dosed, "every crop under the boom, and only those");
            helper.assertValueEqual(a.bonemeal(), 192 - dosed, "one bone meal a dose");
        });
    }

    @GameTest(template = "farm", timeoutTicks = 200)
    public void aHoverDosesAColumnOnceUntilItsCooldownHasPassed(GameTestHelper helper) {
        plot(helper);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Aircraft a = sprayer(helper, pilot, 6.0, 12.5, 64);
        a.sprayKey(pilot);
        int row = SWATH_HI - SWATH_LO + 1;
        helper.runAtTickTime(Aircraft.SPRAY_COOLDOWN - 5, () -> helper.assertValueEqual(a.bonemeal(), 64 - row, "one row, dosed once while it hovers"));
        helper.runAtTickTime(Aircraft.SPRAY_COOLDOWN + 10, () -> {
            helper.assertValueEqual(a.bonemeal(), 64 - 2 * row, "and again once the cooldown passed");
            helper.succeed();
        });
    }

    @GameTest(template = "farm", timeoutTicks = 200)
    public void fromTwentyBlocksUpItDosesNothing(GameTestHelper helper) {
        plot(helper);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Aircraft a = sprayer(helper, pilot, 20.0, 0.5, 64);
        a.sprayKey(pilot);
        pass(helper, a, 1.4, 17, () -> {
            helper.assertValueEqual(grown(helper), 0, "nothing grew");
            helper.assertValueEqual(a.bonemeal(), 64, "nothing spent");
        });
    }

    @GameTest(template = "farm", timeoutTicks = 200)
    public void aBoneMealEventAClaimModCancelsSpendsNothing(GameTestHelper helper) {
        plot(helper);
        for (int x = SWATH_LO; x <= SWATH_HI; x++) {
            for (int z = LO; z <= HI; z++) {
                GameTestMod.protect(helper.absolutePos(new BlockPos(x, CROP, z)));
            }
        }
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Aircraft a = sprayer(helper, pilot, 6.0, 0.5, 64);
        a.sprayKey(pilot);
        pass(helper, a, 1.4, 17, () -> {
            for (int x = SWATH_LO; x <= SWATH_HI; x++) {
                for (int z = LO; z <= HI; z++) {
                    GameTestMod.unprotect(helper.absolutePos(new BlockPos(x, CROP, z)));
                }
            }
            helper.assertValueEqual(grown(helper), 0, "nothing grew where the claim refused it");
            helper.assertValueEqual(a.bonemeal(), 64, "and nothing was spent");
        });
    }

    @GameTest(template = "farm", timeoutTicks = 200)
    public void anEmptyTankSwitchesItOff(GameTestHelper helper) {
        plot(helper);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Aircraft a = sprayer(helper, pilot, 6.0, 0.5, 3);
        a.sprayKey(pilot);
        pass(helper, a, 1.4, 17, () -> {
            helper.assertValueEqual(grown(helper), 3, "three doses");
            helper.assertValueEqual(a.bonemeal(), 0, "the tank is empty");
            helper.assertTrue(!a.spraying(), "and the sprayer switched itself off");
        });
    }

    @GameTest(template = "farm", timeoutTicks = 40)
    public void packingKeepsTheSprayerAndItsBoneMeal(GameTestHelper helper) {
        plot(helper);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Aircraft a = sprayer(helper, pilot, 6.0, 0.5, 100);
        pilot.stopRiding();
        ItemStack packed = a.toItem();
        helper.assertValueEqual(packed.get(RotorcraftContent.SPRAYER.get()), 100, "the packed item keeps the sprayer and its bone meal");
        Vehicle again = Vehicle.create(helper.getLevel(), Rigs.BOX_HELI, a.position(), 0.0f);
        helper.assertTrue(again instanceof Aircraft && again.loadFromItem(packed), "set down again");
        helper.assertTrue(((Aircraft) again).sprayerFitted() && ((Aircraft) again).bonemeal() == 100, "with its sprayer and bone meal");
        helper.succeed();
    }

    @GameTest(template = "farm", timeoutTicks = 200)
    public void aCreativePilotSpraysForFree(GameTestHelper helper) {
        plot(helper);
        Player pilot = helper.makeMockPlayer(GameType.CREATIVE);
        GameType.CREATIVE.updatePlayerAbilities(pilot.getAbilities());
        Aircraft a = sprayer(helper, pilot, 6.0, 0.5, 0);
        a.sprayKey(pilot);
        helper.assertTrue(a.spraying(), "an empty tank sprays for a creative pilot");
        pass(helper, a, 1.4, 17, () -> {
            helper.assertTrue(grown(helper) > 0, "the crops grew");
            helper.assertValueEqual(a.bonemeal(), 0, "and nothing was spent");
        });
    }

    @GameTest(template = "farm", timeoutTicks = 40)
    public void theSprayerIsFittedAndTakenOffByHandItsBoneMealHandedBack(GameTestHelper helper) {
        plot(helper);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        Aircraft a = sprayer(helper, pilot, 6.0, 0.5, 70);
        pilot.stopRiding();
        helper.assertTrue(pilot.getInventory().countItem(RotorcraftContent.CROP_SPRAYER.get()) == 0, "the sprayer went from the hand onto the aircraft");
        pilot.setShiftKeyDown(true);
        com.chunkworks.vanillawheels.api.VehicleProfile p = a.profile();
        Vec3 boom = a.rotate(p.localBlocks(a.aircraft().sprayer().get().at()));
        helper.assertTrue(a.interactAt(pilot, boom, InteractionHand.MAIN_HAND).consumesAction(), "a crouching click on the boom");
        helper.assertTrue(!a.sprayerFitted() && a.bonemeal() == 0, "takes the sprayer off");
        helper.assertValueEqual(pilot.getInventory().countItem(RotorcraftContent.CROP_SPRAYER.get()), 1, "back in hand");
        helper.assertValueEqual(pilot.getInventory().countItem(Items.BONE_MEAL), 70, "with its bone meal");
        helper.succeed();
    }
}
