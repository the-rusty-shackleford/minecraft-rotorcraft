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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A spinning rotor's strike, on the box boom (its main rotor four blocks across from its pivot,
 * 1.875 over the feet): a bird in the disc is cut down, by the rotor and blamed on the pilot, while
 * a cow under the disc and the crew aboard, the rider's head through it, are untouched; a rotor at
 * rest strikes nothing; flying forward, it cuts down a bird in its path.
 */
@GameTestHolder(Rotorcraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StrikeGameTests {
    private static final int SIZE = 40;
    /** The box boom's main rotor: its hub over the feet and along the body, blocks. */
    private static final double HUB_Y = 1.875, HUB_Z = 0.25;

    public StrikeGameTests() {}

    /** effects: returns a mob of {@code type} at the absolute {@code at}, still: no AI, no gravity */
    private static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        T mob = type.create(helper.getLevel());
        helper.assertTrue(mob != null, "a " + type);
        mob.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "airfield", timeoutTicks = 200)
    public void aSpinningRotorCutsDownABirdInItsDiscButNothingUnderItOrAboard(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.aircraft(helper, Rigs.BOX_BOOM, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        LivingEntity rider = Rigs.crew(helper, a);
        a.setScriptedFlight(Rigs.fly(0, 0, 0));
        // A chicken's box (0.7 tall) through the blades' plane, two blocks out; a cow's back (1.4) under the slab.
        Mob bird = still(helper, EntityType.CHICKEN, helper.absoluteVec(new Vec3(22.5, Rigs.FLOOR + HUB_Y - 0.35, 20.5 + HUB_Z)));
        Mob cow = still(helper, EntityType.COW, helper.absoluteVec(new Vec3(18.0, Rigs.FLOOR, 20.5 + HUB_Z)));
        float riderHealth = rider.getHealth(), cowHealth = cow.getHealth();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!bird.isAlive(), "the bird in the disc is cut down"))
                .thenExecute(() -> {
                    DamageSource last = bird.getLastDamageSource();
                    helper.assertTrue(last != null && last.is(RotorcraftContent.ROTOR_STRIKE), "by the rotor: " + last);
                    helper.assertTrue(last.getEntity() == a.getControllingPassenger(), "blamed on the pilot: " + last.getEntity());
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertValueEqual(cow.getHealth(), cowHealth, "the cow under the disc");
                    helper.assertValueEqual(rider.getHealth(), riderHealth, "the rider aboard, its head through the disc");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 100)
    public void aRotorAtRestStrikesNothing(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        // Nobody aboard: the rotor never turns.
        Aircraft a = Rigs.aircraft(helper, Rigs.BOX_BOOM, 20.5, Rigs.FLOOR, 20.5, 0.0f);
        Mob bird = still(helper, EntityType.CHICKEN, helper.absoluteVec(new Vec3(22.5, Rigs.FLOOR + HUB_Y - 0.35, 20.5 + HUB_Z)));
        float health = bird.getHealth();
        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertValueEqual(a.rotor(), 0.0f, "the rotor at rest");
                    helper.assertValueEqual(bird.getHealth(), health, "the bird among its blades unhurt");
                })
                .thenSucceed();
    }

    @GameTest(template = "airfield", timeoutTicks = 300)
    public void flyingForwardItCutsDownABirdInItsPath(GameTestHelper helper) {
        Rigs.floor(helper, SIZE);
        Aircraft a = Rigs.aircraft(helper, Rigs.BOX_BOOM, 8.5, Rigs.FLOOR + 6, 20.5, -90.0f);
        Rigs.crew(helper, a);
        a.setScriptedFlight(Rigs.fly(0, 0, 0));
        Mob[] bird = new Mob[1];
        helper.startSequence()
                .thenIdle(60)   // spooled up, hovering wherever it settled
                .thenExecute(() -> {
                    // Ten blocks ahead (facing +x), at the hub's height as it hovers now.
                    bird[0] = still(helper, EntityType.CHICKEN, new Vec3(a.getX() + 10.0, a.getY() + HUB_Y - 0.35, a.getZ()));
                    a.setScriptedFlight(Rigs.fly(1, 0, 0));
                })
                .thenWaitUntil(() -> helper.assertTrue(!bird[0].isAlive(), "the bird in its path is cut down"))
                .thenExecute(() -> {
                    a.setScriptedFlight(Rigs.fly(0, 0, 0));
                    DamageSource last = bird[0].getLastDamageSource();
                    helper.assertTrue(last != null && last.is(RotorcraftContent.ROTOR_STRIKE), "by the rotor: " + last);
                })
                .thenSucceed();
    }
}
