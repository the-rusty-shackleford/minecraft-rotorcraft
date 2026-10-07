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
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.domain.LiftMotion;
import com.chunkworks.vanillawheels.domain.LiftStatus;
import com.chunkworks.vanillawheels.lift.LiftBlockEntity;
import com.chunkworks.vanillawheels.lift.LiftMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An aircraft on Vanilla Wheels' Mechanic Lift, through its real item and menu: built from its
 * chassis and an engine (its skids take no wheels, and four are refused), made as an aircraft by
 * this protocol's kind (Vanilla Wheels' D-0030), and painted on the deck like any vehicle.
 */
@GameTestHolder(Rotorcraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiftGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(20, Rigs.FLOOR, 12);

    public LiftGameTests() {}

    @GameTest(template = "airfield", timeoutTicks = 200)
    public void theLiftBuildsAnAircraftFromItsChassisAndEngineAndPaintsIt(GameTestHelper helper) {
        Rigs.floor(helper, 40);
        ServerPlayer sp = Rigs.player(helper, "lift-tester", GameType.SURVIVAL, new Vec3(24.5, Rigs.FLOOR, 15.5));
        sp.setYRot(Direction.SOUTH.toYRot());
        ItemStack liftItem = new ItemStack(ModContent.LIFT_ITEM.get());
        sp.setItemInHand(InteractionHand.MAIN_HAND, liftItem);
        BlockPos floor = helper.absolutePos(CONTROLLER).below();
        BlockHitResult down = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        helper.assertTrue(ModContent.LIFT_ITEM.get().place(new BlockPlaceContext(helper.getLevel(), sp, InteractionHand.MAIN_HAND, liftItem, down)).consumesAction(), "the lift is placed");
        BlockHitResult at = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(CONTROLLER)), Direction.UP, helper.absolutePos(CONTROLLER), false);
        helper.assertTrue(helper.getBlockState(CONTROLLER).useWithoutItem(helper.getLevel(), sp, at).consumesAction(), "its menu opens");
        helper.assertTrue(sp.containerMenu instanceof LiftMenu, "the lift's menu");
        LiftMenu menu = (LiftMenu) sp.containerMenu;
        menu.getSlot(LiftMenu.CHASSIS).set(ModContent.chassisStack(Rigs.BOX_HELI));
        menu.getSlot(LiftMenu.ENGINE).set(new ItemStack(ModContent.ENGINE.get()));
        menu.getSlot(LiftMenu.WHEELS).set(new ItemStack(ModContent.WHEEL.get(), 4));
        menu.broadcastChanges();
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.NO_RECIPE, "four wheels its skids have no place for");
        menu.getSlot(LiftMenu.WHEELS).set(ItemStack.EMPTY);
        menu.broadcastChanges();
        helper.assertValueEqual(menu.buildStatus(), LiftStatus.Build.READY, "a chassis and an engine build it");
        helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.BUILD_BUTTON), "built");
        LiftBlockEntity lift = (LiftBlockEntity) helper.getBlockEntity(CONTROLLER);
        List<Vehicle> built = helper.getLevel().getEntitiesOfClass(Vehicle.class, lift.deckBox());
        helper.assertValueEqual(built.size(), 1, "one vehicle on the deck");
        helper.assertTrue(built.get(0) instanceof Aircraft, "made as an aircraft: " + built.get(0));
        Aircraft a = (Aircraft) built.get(0);
        helper.runAtTickTime(LiftMotion.JOB + 4, () -> {
            menu.getSlot(LiftMenu.DYE).set(new ItemStack(Items.RED_DYE));
            menu.broadcastChanges();
            helper.assertValueEqual(menu.paintStatus(), LiftStatus.Paint.READY, "the aircraft on the deck can be painted");
            helper.assertTrue(menu.clickMenuButton(sp, LiftMenu.PAINT_BUTTON), "painted");
            helper.assertValueEqual(a.paint(), DyeColor.RED, "red");
            Rigs.logOff(sp);
            helper.succeed();
        });
    }
}
