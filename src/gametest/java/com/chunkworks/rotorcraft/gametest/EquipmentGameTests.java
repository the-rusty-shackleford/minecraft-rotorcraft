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

import com.chunkworks.rotorcraft.RotorcraftContent;
import com.chunkworks.rotorcraft.SlungLoad;
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.rotorcraft.api.SlingProfile;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The protocol's standard equipment as it ships: the Sling Container's two profiles decode and it is
 * made as a slung load, with its doors, its two chests, its room for four grown animals, its rope and
 * a truck's durability; the container and the Crop Sprayer each have their recipe; and the protocol's
 * creative tab lists every aircraft and slung load with its chassis, and the sprayer, which Vanilla
 * Wheels' tab leaves out.
 */
@GameTestHolder(Rotorcraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentGameTests {
    private static final ResourceLocation CONTAINER = Rotorcraft.id("sling_container");

    public EquipmentGameTests() {}

    @GameTest(template = "farm", timeoutTicks = 20)
    public void theSlingContainerShipsAsASlungLoadWithDoorsChestsCargoAndARope(GameTestHelper helper) {
        VehicleProfile p = VanillaWheels.profile(helper.getLevel().registryAccess(), CONTAINER).map(h -> h.value()).orElse(null);
        helper.assertTrue(p != null, "its Vanilla Wheels profile decodes");
        SlingProfile sp = Rotorcraft.slingLoad(helper.getLevel().registryAccess(), CONTAINER).orElse(null);
        helper.assertTrue(sp != null && sp.rope() == 4.0, "its sling profile decodes: a rope of four");
        helper.assertTrue(p.engine().isEmpty() && p.seats().isEmpty(), "no engine, no seats");
        helper.assertValueEqual(p.doors().size(), 2, "two doors");
        helper.assertValueEqual(p.storage().map(VehicleProfile.Storage::slots).orElse(0), 108, "two chests of six rows");
        helper.assertTrue(p.cargo().isPresent() && p.cargo().get().adults() == 4, "room for four grown animals");
        helper.assertValueEqual(p.wheels().parts(), 0, "feet, not wheels");
        helper.assertValueEqual(p.durability(), 8.0, "a truck's durability (Vanilla Wheels' D-0034)");
        Vehicle v = Vehicle.create(helper.getLevel(), CONTAINER, helper.absoluteVec(new Vec3(12, 2, 12)), 0.0f);
        helper.assertTrue(v instanceof SlungLoad, "made as a slung load: " + v);
        helper.succeed();
    }

    @GameTest(template = "farm", timeoutTicks = 20)
    public void theAircraftTheLoadsAndTheSprayerAreInThisProtocolsTabAndNotInVanillaWheels(GameTestHelper helper) {
        CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        CreativeModeTab ours = RotorcraftContent.CREATIVE_TAB.get();
        ours.buildContents(params);
        Set<ResourceLocation> vehicles = profiles(ours, ModContent.VEHICLE_ITEM.get()), chassis = profiles(ours, ModContent.CHASSIS.get());
        for (ResourceLocation id : List.of(Rigs.BOX_HELI, Rigs.BOX_BOOM, Rigs.BOX_CRATE, CONTAINER)) {
            helper.assertTrue(vehicles.contains(id) && chassis.contains(id), id + " and its chassis: " + vehicles);
        }
        helper.assertTrue(ours.getDisplayItems().stream().anyMatch(st -> st.is(RotorcraftContent.CROP_SPRAYER.get())), "the sprayer");
        CreativeModeTab wheels = ModContent.CREATIVE_TAB.get();
        wheels.buildContents(params);
        Set<ResourceLocation> theirs = profiles(wheels, ModContent.VEHICLE_ITEM.get());
        helper.assertTrue(!theirs.contains(Rigs.BOX_HELI) && !theirs.contains(CONTAINER), "none in Vanilla Wheels': " + theirs);
        helper.assertTrue(wheels.getDisplayItems().stream().noneMatch(st -> st.is(RotorcraftContent.CROP_SPRAYER.get())), "nor the sprayer");
        helper.succeed();
    }

    /** effects: the profiles of the vehicle items (or chassis), {@code item}, that {@code tab} lists */
    private static Set<ResourceLocation> profiles(CreativeModeTab tab, net.minecraft.world.item.Item item) {
        return tab.getDisplayItems().stream().filter(st -> st.is(item))
                .map(st -> st.get(ModContent.VEHICLE.get())).collect(Collectors.toSet());
    }

    @GameTest(template = "farm", timeoutTicks = 20)
    public void theContainerAndTheSprayerHaveTheirRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        RecipeHolder<?> container = recipes.byKey(Rotorcraft.id("sling_container")).orElse(null);
        helper.assertTrue(container != null, "the container's recipe");
        ItemStack made = container.value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(made.is(ModContent.VEHICLE_ITEM.get()) && CONTAINER.equals(made.get(ModContent.VEHICLE.get())), "makes the container itself: " + made);
        RecipeHolder<?> sprayer = recipes.byKey(Rotorcraft.id("crop_sprayer")).orElse(null);
        helper.assertTrue(sprayer != null && sprayer.value().getResultItem(helper.getLevel().registryAccess()).is(RotorcraftContent.CROP_SPRAYER.get()), "the sprayer's recipe");
        helper.succeed();
    }
}
