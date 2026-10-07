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
package com.chunkworks.rotorcraft;

import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.rotorcraft.api.SlingProfile;
import com.chunkworks.rotorcraft.domain.Flight;
import com.chunkworks.rotorcraft.domain.Sling;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Suspension;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A slung load: a Vanilla Wheels vehicle with no seats and no engine -- its chests, its cargo
 * behind its doors, its paint are that protocol's -- whose tow link hangs it from an aircraft's
 * hook on a rope (D-0002 here). Loaded on the ground, hooked from the air with the hook key, it
 * swings under the aircraft and follows it ({@link Sling}); set down, it rests and its rope goes
 * slack. Nothing aboard is hurt by any of it; a crash or a fall wears the load. Towing's machinery
 * is Vanilla Wheels' own: the link, its save, who simulates it (whoever flies the aircraft), the
 * tick order, the lights, recall.
 */
public class SlungLoad extends Vehicle {
    /** The eye on its rope as this side last stepped it; null until hooked and stepped. */
    @Nullable private Sling sling;
    @Nullable private VehicleProfile cachedFor;
    @Nullable private SlingProfile cachedSling;
    private final net.minecraft.core.BlockPos.MutableBlockPos probe = new net.minecraft.core.BlockPos.MutableBlockPos();

    public SlungLoad(EntityType<? extends Vehicle> type, Level level) {
        super(type, level);
    }

    /** effects: returns where this load's rope meets it, or null while the registries do not have it */
    @Nullable
    public SlingProfile sling() {
        VehicleProfile p = profile();
        if (p != cachedFor) {
            cachedFor = p;
            cachedSling = p == null ? null : Rotorcraft.slingLoad(level().registryAccess(), profileId()).orElse(null);
        }
        return cachedSling;
    }

    /** effects: returns its lift eye in the world, turned with the body, or null without a sling profile */
    @Nullable
    public Vec3 eyePoint() {
        SlingProfile sp = sling();
        VehicleProfile p = profile();
        return sp == null || p == null ? null : position().add(rotate(p.localBlocks(sp.lift())));
    }

    /** effects: returns whether it rests on something -- the ground, or within a block of it, or a fluid's surface: what the hook needs before it lets go */
    public boolean resting() {
        return onGround() || !level().noCollision(this, getBoundingBox().move(0.0, -1.0, 0.0)) || clearance() < 0.05;
    }

    /** effects: returns whether it is down on its floor -- the ground's collision, or a floor (a fluid's surface) within a hair under it: past this its aircraft's descent is eased onto its eye instead */
    public boolean landed() {
        return onGround() || clearance() < 0.05;
    }

    /**
     * effects: ticks as a vehicle does; while it hangs from an aircraft, its fall distance is reset
     * every tick -- held on its rope it is not falling, and the game would hand every swing and
     * every block of its descent to the ground at once when it touched down (a crate was wrecked
     * that way, set down softly). Let go, it falls as anything does.
     */
    @Override
    public void tick() {
        super.tick();
        if (sling() != null && tower() instanceof Aircraft) {
            resetFallDistance();
        }
    }

    /** effects: forgets the eye's swing, for a fresh hooking */
    void hooked() {
        sling = null;
    }

    /** effects: returns how far its underside is over the floor below its middle, blocks, at most {@link Aircraft#PROBE}: what its aircraft eases its descent by while it hangs ({@link Floors#below}) */
    public double clearance() {
        return Floors.below(level(), probe, getX(), getY(), getZ(), Aircraft.PROBE);
    }

    /** effects: moves as a vehicle does, except that water and lava are floors: a load set down on a lake rests on its surface and nothing aboard goes under */
    @Override
    public void move(MoverType type, Vec3 delta) {
        super.move(type, sling() == null ? delta : Floors.onFluid(level(), probe, getX(), getY(), getZ(), delta));
    }

    /** effects: the ground as the drawn pose reads it, a fluid's surface included */
    @Override
    protected com.chunkworks.vanillawheels.domain.Terrain.Columns columns() {
        return sling() == null ? super.columns() : Floors.withFluids(super.columns(), level(), getY());
    }

    /**
     * effects: one tick of hanging from {@code tower}'s hook: the eye stepped on its rope from
     * where it is ({@link Sling}), the body moved through the world to put it there, so it rests on
     * and scrapes the ground; what the world refused of the move is gone, and on the server wears
     * it; turned a little toward the aircraft's heading; snagged past its rope, let go. A tower
     * with no hook tows it as Vanilla Wheels does.
     */
    @Override
    protected void follow(Vehicle tower) {
        SlingProfile sp = sling();
        Vec3 hook = tower instanceof Aircraft aircraft ? aircraft.hookPoint() : null;
        Vec3 eye = eyePoint();
        if (sp == null || hook == null || eye == null) {
            super.follow(tower);
            return;
        }
        Sling from = sling == null ? new Sling(eye.x, eye.y, eye.z, getX() - xo, getY() - yo, getZ() - zo)
                : new Sling(eye.x, eye.y, eye.z, sling.vx(), sling.vy(), sling.vz());
        Sling to = from.step(hook.x, hook.y, hook.z, sp.rope());
        Vec3 want = new Vec3(to.x() - eye.x, to.y() - eye.y, to.z() - eye.z);
        double x0 = getX(), y0 = getY(), z0 = getZ();
        setDeltaMovement(want);
        move(MoverType.SELF, want);
        Vec3 got = new Vec3(getX() - x0, getY() - y0, getZ() - z0);
        sling = Sling.struck(eye.x + got.x, eye.y + got.y, eye.z + got.z, want.x, want.y, want.z, got.x, got.y, got.z);
        setDeltaMovement(new Vec3(sling.vx(), sling.vy(), sling.vz()));
        setYRot(Mth.rotLerp(0.08f, getYRot(), tower.getYRot()));
        showSpeed((float) Math.hypot(got.x, got.z));
        if (!level().isClientSide()) {
            crashed(Flight.CRASH.impact(from.vx(), from.vy(), from.vz(), want.x, want.y, want.z, got.x, got.y, got.z));
            Vec3 now = eyePoint();
            if (now != null && tower.trailer() == this && Sling.snagged(now.distanceTo(hook), sp.rope())) {
                unhitch();
                level().playSound(null, now.x, now.y, now.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0f, 0.6f);
            }
        }
    }

    /** effects: hangs level in the air (one point holds it, over its middle); on the ground, Vanilla Wheels' pose; the server takes a player-flown chain's pose from the pilot */
    @Override
    protected void poseTowed(VehicleProfile p, Vehicle tower) {
        if (sling() == null) {
            super.poseTowed(p, tower);
            return;
        }
        if (!level().isClientSide() && playerAtTheHead()) {
            takeSharedPose();
            return;
        }
        if (resting()) {
            rideTheGround(p);
        } else {
            pose(Suspension.LEVEL);
        }
        sharePose();
    }

    /** Nothing aboard is handed a fall; the load takes it, as the sink it met the ground at. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (sling() == null) {
            return super.causeFallDamage(fallDistance, multiplier, source);
        }
        if (!level().isClientSide() && Float.isFinite(fallDistance) && fallDistance > 0) {
            crashed(Flight.CRASH.touchdown(Math.sqrt(2.0 * Sling.GRAVITY * fallDistance)));
        }
        return false;
    }

    @Override
    protected boolean runsOver() {
        return sling() == null;
    }

    /** effects: a crouching empty-handed click on its lift eye, while it hangs from a hook and rests on the ground, unhooks it; anything else is Vanilla Wheels' */
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        Vec3 eye = eyePoint();
        if (eye != null && tower() != null && player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()
                && eye.subtract(position()).distanceTo(hit) <= 1.5) {
            if (!level().isClientSide()) {
                if (resting()) {
                    unhitch();
                } else {
                    player.displayClientMessage(Component.translatable("rotorcraft.hook.set_down_first"), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        return super.interactAt(player, hit, hand);
    }
}
