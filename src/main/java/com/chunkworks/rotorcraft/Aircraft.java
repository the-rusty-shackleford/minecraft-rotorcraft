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

import com.chunkworks.carried.api.Carried;
import com.chunkworks.rotorcraft.api.AircraftProfile;
import com.chunkworks.rotorcraft.api.Rotorcraft;
import com.chunkworks.rotorcraft.domain.Airframe;
import com.chunkworks.rotorcraft.domain.Exit;
import com.chunkworks.rotorcraft.domain.Flight;
import com.chunkworks.rotorcraft.domain.FlightInput;
import com.chunkworks.rotorcraft.domain.Sling;
import com.chunkworks.rotorcraft.domain.Strike;
import com.chunkworks.rotorcraft.domain.Swath;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Crash;
import com.chunkworks.vanillawheels.domain.Hull;
import com.chunkworks.vanillawheels.domain.Suspension;
import com.chunkworks.vanillawheels.domain.Vec;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Every aircraft in the world is one of these: a Vanilla Wheels vehicle (its seats, chests, cargo,
 * paint, keys, fuel and condition are that protocol's) that flies instead of driving (D-0001 here).
 * The pilot's client steps {@link Flight} and moves it, the server mirrors and judges, as Vanilla
 * Wheels' boat rule has it; with nobody at the controls the server flies it unpowered, so it
 * settles to the ground.
 *
 * <p>Nobody aboard is ever hurt by the flying: no fall reaches a rider, and the descent is capped
 * so that the floor is never met hard while the rotor turns or autorotates. A crash wears the
 * aircraft instead: the server reads what the world refused of each move -- its own, or the moves
 * the pilot's client reports -- and charges {@link Flight#CRASH}, judged by Vanilla Wheels (its
 * D-0031). Worn to nothing in the air, it comes down autorotating, its engine dead, and is a wreck
 * only once it stands on something. Riders get out with the get-out key within {@link #GET_OUT} of
 * the floor, never higher; Shift is the collective here, never a way out (Vanilla Wheels' vertical
 * controls).
 *
 * <p>It carries slung loads on its hook (see {@link SlungLoad}) and, where its profile has a mount,
 * a crop sprayer that doses the crops under its boom with the bone meal in its tank.
 */
public class Aircraft extends Vehicle {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    /** The rotor's speed as every side draws and hears it, 0..1: the server's model of it. */
    private static final EntityDataAccessor<Float> DATA_ROTOR = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.FLOAT);
    /** Whether a crop sprayer is fitted. */
    private static final EntityDataAccessor<Boolean> DATA_SPRAYER = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.BOOLEAN);
    /** The bone meal in its tank. */
    private static final EntityDataAccessor<Integer> DATA_BONEMEAL = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.INT);
    /** Whether it is spraying. */
    private static final EntityDataAccessor<Boolean> DATA_SPRAYING = SynchedEntityData.defineId(Aircraft.class, EntityDataSerializers.BOOLEAN);

    /** How far over the floor a rider may get out, blocks: a drop nobody is hurt by. */
    public static final double GET_OUT = 3.0;
    /** How far over a resting load's eye the hook stops coming down, blocks. */
    public static final double HOOK_CLEAR = 0.25;
    /** The most bone meal a sprayer's tank holds. */
    public static final int TANK = 256;
    /** The highest a boom doses crops from, blocks over them. */
    public static final double SPRAY_HEIGHT = 12.0;
    /** Ticks before a column is dosed again: hovering does not empty the tank on one row. */
    public static final int SPRAY_COOLDOWN = 40;
    /** The most doses a tick. */
    public static final int SPRAY_DOSES = 32;
    /** How far below itself it looks for the floor, blocks. */
    static final int PROBE = 32;
    /** The rotor's turn a tick at full speed, radians, for drawing: a rotor's {@code speed} multiplies it. */
    public static final double SPIN = 0.9;

    private Flight flight = Flight.landed(0.0);
    @Nullable private FlightInput scripted;
    /** The server's model of the rotor while a player flies it: what everyone sees and hears, what burns fuel. */
    private double serverSpool;
    /** The floor's distance under it as the last step probed it. */
    private double clearance = PROBE;
    /** Set while a /kill takes it: no waiting to come down. */
    private boolean killed;
    /**
     * Set while a wreck made in the air comes down, to be packed once it stands (D-0004). A broken
     * aircraft set down from its packed item is not one: it stays where it is put, unflyable, until
     * it is repaired.
     */
    private boolean wreckComingDown;
    /** The rotor's accumulated turn on the client, radians, this tick and the last. */
    private double rotorAngle, rotorAngleO;

    /** The boom where it was last tick, and the columns dosed and when, for the spraying. */
    @Nullable private Vec3 lastBoom;
    private double lastBoomHeading;
    private final Long2LongOpenHashMap dosed = new Long2LongOpenHashMap();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos sprayAt = new BlockPos.MutableBlockPos();
    private double boomY;
    private long sprayTick;
    private int doses;
    @Nullable private Player sprayer;
    private final Swath.Columns sprayColumn = this::sprayColumn;

    /** The profiles this was last read from, so the registry is asked once a profile, not every tick. */
    @Nullable private VehicleProfile cachedFor;
    @Nullable private AircraftProfile cachedAircraft;
    @Nullable private Airframe cachedAirframe;
    /** The hull's probe points (x, y, z triples, blocks in the body's frame), or null for none: the nose and tail then. */
    @Nullable private double[] cachedHull;
    /** {reach, top}: how far across and how high anything of the hull and the rotors reaches, blocks. */
    private double[] cachedReach = {0.0, 0.0};
    /** The rotors' discs in the body's frame (rotorDiscs), or null for none. */
    @Nullable private double[] cachedRotors;
    /** What a disc's neighbourhood holds this tick, the list reused tick to tick. */
    private final List<LivingEntity> nearRotor = new ArrayList<>();
    private final Predicate<LivingEntity> strikable = e -> e.isAlive() && !carries(e);
    private static final EntityTypeTest<Entity, LivingEntity> LIVING = EntityTypeTest.forClass(LivingEntity.class);

    public Aircraft(EntityType<? extends Vehicle> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROTOR, 0.0f);
        builder.define(DATA_SPRAYER, false);
        builder.define(DATA_BONEMEAL, 0);
        builder.define(DATA_SPRAYING, false);
    }

    // --- the profile ----------------------------------------------------------

    /** effects: returns how this aircraft flies, or null while the registries do not have it */
    @Nullable
    public AircraftProfile aircraft() {
        VehicleProfile p = profile();
        if (p != cachedFor) {
            cachedFor = p;
            cachedAircraft = p == null ? null : Rotorcraft.aircraft(level().registryAccess(), profileId()).orElse(null);
            cachedAirframe = cachedAircraft == null || p.engine().isEmpty() ? null : cachedAircraft.airframe(p);
            List<Hull.Box> boxes = cachedAircraft == null ? List.of() : cachedAircraft.hullBoxes(p);
            cachedHull = boxes.isEmpty() ? null : Hull.points(boxes);
            cachedReach = cachedAircraft == null ? new double[] {0.0, 0.0} : Hull.reach(boxes, cachedAircraft.discs(p));
            cachedRotors = cachedAircraft == null ? null : rotorDiscs(cachedAircraft, p);
        }
        return cachedAircraft;
    }

    /**
     * effects: returns the rotors that reach anything as runs of seven, blocks in the body's frame:
     * the pivot (x, y, z), the unit axis (x, y, z), the reach; null for none
     */
    @Nullable
    private static double[] rotorDiscs(AircraftProfile ap, VehicleProfile p) {
        double[] out = new double[7 * ap.rotors().size()];
        int k = 0;
        for (AircraftProfile.Rotor r : ap.rotors()) {
            Vec c = p.localBlocks(r.pivot()), a = p.localBlocks(r.axis());
            double len = Math.sqrt(a.x() * a.x() + a.y() * a.y() + a.z() * a.z());
            if (r.radius() <= 0.0 || len == 0.0) {
                continue;
            }
            out[k++] = c.x();
            out[k++] = c.y();
            out[k++] = c.z();
            out[k++] = a.x() / len;
            out[k++] = a.y() / len;
            out[k++] = a.z() / len;
            out[k++] = p.blocks(r.radius());
        }
        return k == 0 ? null : java.util.Arrays.copyOf(out, k);
    }

    /** effects: returns the numbers it flies by, or null without an aircraft profile */
    @Nullable
    public Airframe airframe() {
        aircraft();
        return cachedAirframe;
    }

    /** effects: returns the flight as this side last stepped it */
    public Flight flight() {
        return flight;
    }

    // --- flying -------------------------------------------------------------

    /** effects: returns whether this side flies it: the pilot's client, or the server when no player is at the controls */
    private boolean flownHere() {
        return level().isClientSide() ? isControlledByLocalInstance() : !(getControllingPassenger() instanceof Player);
    }

    /** effects: returns whether the engine can drive the rotor: someone or a script at the controls, fuel, and not a wreck ({@code hasFuel} asks both) */
    public boolean engineCanRun() {
        return (getControllingPassenger() != null || scripted != null) && hasFuel();
    }

    /**
     * effects: from now on, with no player at the controls, the server flies by {@code input} (its
     * power, ground and clearance replaced by the truth each tick): what a gametest flies by; null
     * hands the controls back
     */
    public void setScriptedFlight(@Nullable FlightInput input) {
        this.scripted = input;
    }

    @Override
    protected void takeTheWheel() {
        super.takeTheWheel();
        // Fly on from what the world has: its motion, its heading, the rotor as it is turning.
        flight = new Flight(getX() - xo, getY() - yo, getZ() - zo, Flight.wrap(Math.toRadians(getYRot())), 0.0,
                Mth.clamp(entityData.get(DATA_ROTOR), 0.0f, 1.0f), 0.0, 0.0);
    }

    @Override
    protected void stepAtTheWheel(VehicleProfile p) {
        Airframe a = airframe();
        if (a == null) {
            super.stepAtTheWheel(p);
            return;
        }
        clearance = clearance(p);
        boolean standing = standing(clearance);
        boolean powered = engineCanRun();
        // The descent is capped by the floor under whatever hangs lowest: a load on the hook is set down
        // as gently as the aircraft itself. The load follows after this step, so its height is a tick
        // old: the cap reads it a tick's sink lower, braking a tick sooner. Once the load is down, the
        // floor is its eye: the hook comes down softly onto it and no lower ({@link #overTheLoad}).
        double cushion = clearance;
        if (trailer() instanceof SlungLoad load) {
            cushion = load.landed() ? Math.min(clearance, overLoad(load))
                    : Math.min(clearance, Math.max(0.0, load.clearance() - Math.max(0.0, -flight.vy())));
        }
        FlightInput in = level().isClientSide() ? com.chunkworks.rotorcraft.client.FlightControls.input(this, powered, standing, cushion)
                : scripted != null ? scripted.in(powered, standing, cushion)
                : FlightInput.unpiloted(standing, cushion);
        flight = flight.step(in, a);
        setYRot((float) Math.toDegrees(flight.heading()));
        Vec3 want = new Vec3(flight.vx(), flight.vy(), flight.vz());
        double x0 = getX(), y0 = getY(), z0 = getZ();
        setDeltaMovement(want);
        move(MoverType.SELF, want);
        Vec3 got = new Vec3(getX() - x0, getY() - y0, getZ() - z0);
        if (got.distanceToSqr(want) > 1.0e-10) {
            // What the world refused is gone: the velocity loses its part into what stopped it and
            // keeps the rest, so it slides along a wall and does not drive into it again.
            flight = flight.struck(want.x, want.y, want.z, got.x, got.y, got.z);
            if (!level().isClientSide()) {
                crashed(Flight.CRASH.impact(want.x, want.y, want.z, got.x, got.y, got.z));
            }
        }
        setDeltaMovement(new Vec3(flight.vx(), flight.vy(), flight.vz()));
        showSpeed((float) flight.speed());
        if (level().isClientSide()) {
            com.chunkworks.rotorcraft.client.FlightControls.report(this, flight, in);
        }
    }

    @Override
    protected void poseAtTheWheel(VehicleProfile p) {
        if (aircraft() == null || standing(clearance)) {
            super.poseAtTheWheel(p);
            return;
        }
        pose(new Suspension(0.0, flight.pitch(), flight.roll()));
        sharePose();
    }

    /** effects: returns whether it stands on something: the ground's collision, or a floor (water) within a hair under it */
    public boolean standing(double clearance) {
        return onGround() || clearance < 0.05;
    }

    /**
     * effects: returns how far its underside is over the floor below it -- the first collision or
     * fluid surface -- blocks, at most {@link #PROBE}: under its middle and, within a few blocks of
     * the floor, under each of its skids or wheels, the least of them
     */
    double clearance(VehicleProfile p) {
        double middle = floorBelow(getX(), getZ());
        if (middle > 6.0) {
            return middle;
        }
        double least = middle;
        for (VehicleProfile.WheelPosition w : p.wheels().positions()) {
            Vec3 at = position().add(rotate(new Vec(-p.blocks(w.right()), 0.0, p.blocks(w.forward()))));
            least = Math.min(least, floorBelow(at.x, at.z));
        }
        return least;
    }

    /** effects: returns how far the floor lies under this body's y in the column at (x, z), blocks, at most PROBE ({@link Floors#below}) */
    private double floorBelow(double x, double z) {
        return Floors.below(level(), probe, x, getY(), z, PROBE);
    }

    /** effects: the ground as the drawn pose reads it, a fluid's surface included: settled on a lake it rides the surface, not the lakebed */
    @Override
    protected com.chunkworks.vanillawheels.domain.Terrain.Columns columns() {
        return aircraft() == null ? super.columns() : Floors.withFluids(super.columns(), level(), getY());
    }

    /** effects: returns an aircraft's crash cost while it has an airframe to fly by; nothing else is judged (Vanilla Wheels' D-0031) */
    @Nullable
    @Override
    protected Crash crashes() {
        return airframe() == null ? null : Flight.CRASH;
    }

    /** effects: returns what the flight itself can change in a tick: its acceleration or brake and its turn across, its climb or sink, and a hair */
    @Override
    protected double ownChange() {
        Airframe a = airframe();
        return a == null ? super.ownChange() : Math.max(a.acceleration(), a.brake()) + a.maxSpeed() * a.yawRate() + a.verticalAcceleration() + 0.02;
    }

    /** effects: wears it as Vanilla Wheels does, heard as a crash (a load on the hook is worn without it) */
    @Override
    protected void crashed(int wear) {
        if (wear > 0 && !isRemoved()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.6f, 0.6f);
        }
        super.crashed(wear);
    }

    /** effects: true: Space climbs, Left Shift descends, R gets out, and Shift never lets a rider off (Vanilla Wheels' D-0031) */
    @Override
    public boolean verticalControls() {
        return true;
    }

    // --- the air's rules --------------------------------------------------------

    /** Nothing aboard is handed a fall: the aircraft's own touchdowns are judged as crashes, and nobody is hurt by them. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return aircraft() == null && super.causeFallDamage(fallDistance, multiplier, source);
    }

    /** effects: a wreck in the air comes down first: until it stands on something, nothing is destroyed (a /kill excepted) */
    @Override
    protected void destroy(DamageSource source) {
        VehicleProfile p = profile();
        if (aircraft() != null && p != null && !killed && !standing(clearance(p))) {
            wreckComingDown = true;
            return;
        }
        wreckComingDown = false;
        super.destroy(source);
    }

    @Override
    public void kill() {
        killed = true;
        try {
            super.kill();
        } finally {
            killed = false;
        }
    }

    @Override
    protected boolean runsOver() {
        return aircraft() == null;
    }

    @Override
    protected boolean breaksFragile() {
        return aircraft() == null;
    }

    /** effects: returns whether fuel burns: whenever the engine drives the rotor, hovering or sitting with it spooled */
    @Override
    protected boolean burnsFuel() {
        return aircraft() == null ? super.burnsFuel() : engineCanRun() && rotor() > 0.0f;
    }

    @Override
    public boolean engineRunning() {
        return aircraft() == null ? super.engineRunning() : rotor() > 0.01f;
    }

    /** effects: returns the engine's note: the rotor's speed, rising a little with forward speed */
    @Override
    public float engineLoad() {
        Airframe a = airframe();
        return a == null ? super.engineLoad() : rotor() * (0.75f + 0.25f * Math.min(1.0f, Math.abs(speed()) / (float) a.maxSpeed()));
    }

    /**
     * effects: the footprint's walls in the air: none of the hull's points may end inside a block --
     * its boxes' when it names them, else the nose's and tail's anywhere up the body's height
     * (Vanilla Wheels' {@code hullClamp}, its D-0031); on the ground, Vanilla Wheels' car rule. Which is decided by
     * the ground flag the move starts with, which the pilot's client and the server's re-run of its
     * move both have -- a probe only the client made would have the server refuse the pilot's moves.
     * A move that starts in a block is let through, so it can always back out.
     */
    @Override
    protected Vec3 footprintClamp(Vec3 delta) {
        if (aircraft() == null || profile() == null || onGround()) {
            return super.footprintClamp(delta);
        }
        return hullClamp(overTheLoad(delta));
    }

    /** effects: returns the hull's probe points from the aircraft profile, or null for none: the nose and tail then */
    @Nullable
    @Override
    protected double[] hullPoints() {
        aircraft();
        return cachedHull;
    }

    /**
     * effects: returns {@code delta} with its descent cut short so the hook, over a load that is down
     * on its floor while still hooked, comes no lower than {@link #HOOK_CLEAR} over the load's eye:
     * a rope cannot push, and a hooked load, being of its train, is no obstacle to the body (Vanilla
     * Wheels' {@code canCollideWith}), so without this the aircraft sank through its own load
     */
    private Vec3 overTheLoad(Vec3 delta) {
        if (delta.y >= 0.0 || !(trailer() instanceof SlungLoad load) || !load.landed()) {
            return delta;
        }
        double room = overLoad(load);
        return delta.y < -room ? new Vec3(delta.x, -room, delta.z) : delta;
    }

    /** effects: returns how far the hook may still come down over {@code load}: to HOOK_CLEAR over its eye, and never less than nothing */
    private double overLoad(SlungLoad load) {
        Vec3 hook = hookPoint(), eye = load.eyePoint();
        return hook == null || eye == null ? Double.MAX_VALUE : Math.max(0.0, hook.y - eye.y - HOOK_CLEAR);
    }

    /** effects: returns a box round everything of it that is drawn -- hull and rotors -- whichever way it faces, so a blade alone in view still draws it */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB own = super.getBoundingBoxForCulling();
        aircraft();
        double reach = cachedReach[0], top = cachedReach[1];
        if (reach <= 0.0) {
            return own;
        }
        return own.minmax(new AABB(getX() - reach, getY() - 1.0, getZ() - reach, getX() + reach, getY() + top + 1.0, getZ() + reach));
    }

    // --- the rotors' strike ----------------------------------------------------------

    /**
     * effects: on the server, strikes each living thing a spinning rotor's disc touches
     * ({@link Strike}), the discs tilted with the body as they are drawn: damage by the rotor's
     * speed, blamed on the pilot so the server's PvP rule and teams hold, and the game's knockback
     * away from the aircraft. Nothing aboard is struck (the box boom's rider, its head through the
     * disc, was killed without that); a slung load hangs on its rope, far under any disc. Blocks
     * are untouched: the blades still pass through trees and walls.
     */
    private void strike() {
        double damage = Strike.damage(serverSpool);
        double[] rotors = cachedRotors;
        if (damage <= 0.0 || rotors == null) {
            return;
        }
        Suspension s = suspension(1.0f);
        DamageSource source = null;
        for (int i = 0; i < rotors.length; i += 7) {
            Vec3 hub = position().add(posed(new Vec(rotors[i], rotors[i + 1], rotors[i + 2]), s)).add(0.0, s.lift(), 0.0);
            // Vanilla's rotations turn by its sine table: the axis is a hair off unit length after them.
            Vec3 axis = posed(new Vec(rotors[i + 3], rotors[i + 4], rotors[i + 5]), s).normalize();
            double reach = rotors[i + 6];
            Strike.Disc disc = new Strike.Disc(hub.x, hub.y, hub.z, axis.x, axis.y, axis.z, reach);
            nearRotor.clear();
            level().getEntities(LIVING, new AABB(hub, hub).inflate(reach + Strike.HALF_THICKNESS), strikable, nearRotor);
            for (LivingEntity e : nearRotor) {
                AABB b = e.getBoundingBox();
                if (Strike.touches(disc, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)) {
                    if (source == null) {
                        source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                .getHolderOrThrow(RotorcraftContent.ROTOR_STRIKE), this, getControllingPassenger());
                    }
                    e.hurt(source, (float) damage);
                }
            }
        }
        nearRotor.clear();
    }

    /** effects: returns whether {@code e} rides this aircraft, in a seat or on another rider */
    private boolean carries(Entity e) {
        return e.getRootVehicle() == this;
    }

    // --- the tick ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        VehicleProfile p = profile();
        if (aircraft() == null || p == null) {
            return;
        }
        resetFallDistance();
        if (level().isClientSide()) {
            rotorAngleO = rotorAngle;
            rotorAngle += rotor() * SPIN;
            if (spraying()) {
                com.chunkworks.rotorcraft.client.Mist.spray(this, p);
            }
            return;
        }
        // The rotor as everyone else sees it: this side's own flight if it flies it, else the same rule from the same facts.
        Airframe a = airframe();
        if (flownHere()) {
            serverSpool = flight.spool();
        } else {
            serverSpool = engineCanRun() ? Math.min(1.0, serverSpool + 1.0 / a.spoolTicks()) : Math.max(0.0, serverSpool - 0.5 / a.spoolTicks());
        }
        entityData.set(DATA_ROTOR, (float) serverSpool);
        strike();
        // A wreck made in the air is packed once it has come down. One set down broken from its item
        // stays to be repaired: packing it again as it stood was why a broken Huey could not be mended.
        if (wreckComingDown && condition() == 0 && !isRemoved() && standing(clearance(p))) {
            wreckComingDown = false;
            super.destroy(damageSources().generic());
            return;
        }
        if (condition() > 0) {
            wreckComingDown = false;
        }
        if (spraying()) {
            spray(p);
        } else {
            lastBoom = null;
        }
    }

    /** effects: returns the rotor's speed, 0..1: this side's own flight where it flies it, else the server's model */
    public float rotor() {
        return airframe() != null && flownHere() ? (float) flight.spool() : entityData.get(DATA_ROTOR);
    }

    /** effects: returns the rotor's accumulated turn {@code partialTick} of the way through this tick, radians, for drawing */
    public double rotorAngle(float partialTick) {
        return Mth.lerp(partialTick, rotorAngleO, rotorAngle);
    }

    // --- getting out -------------------------------------------------------------

    /** effects: on the server, lets {@code rider} out if it stands on something or hovers within {@link #GET_OUT} of the floor; otherwise tells them it is too high */
    @Override
    public void getOut(Player rider) {
        VehicleProfile p = profile();
        if (rider.getVehicle() != this || p == null) {
            return;
        }
        double clear = clearance(p);
        if (standing(clear) || clear <= GET_OUT) {
            rider.stopRiding();
        } else {
            rider.displayClientMessage(Component.translatable("rotorcraft.too_high"), true);
        }
    }

    /**
     * effects: returns where {@code rider} stands on getting out: on the ground out of their own
     * seat's door ({@link Exit}), else the other side's; failing both (water, a drop), at a door at
     * the aircraft's own height; failing that, Vanilla Wheels' rule. The game asks while the rider
     * is still in the seat, so where they are is where the seat is. Vanilla Wheels looks for a floor
     * only a block under the body's top, so a Huey's pilot got out onto its roof.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        VehicleProfile p = profile();
        AircraftProfile ap = aircraft();
        if (p != null && ap != null) {
            Vec3 seat = rider.position().subtract(position()).yRot(getYRot() * Mth.DEG_TO_RAD);
            List<Exit.Spot> doors = Exit.doors(seat.x, seat.z, ap.hullBoxes(p), p.body().width() / 2.0, rider.getBbWidth() / 2.0);
            for (Exit.Spot door : doors) {
                Vec3 ground = groundUnder(position().add(rotate(new Vec(door.x(), 0.0, door.z()))), rider);
                if (ground != null) {
                    return ground;
                }
            }
            for (Exit.Spot door : doors) {
                Vec3 at = position().add(rotate(new Vec(door.x(), 0.0, door.z())));
                if (DismountHelper.canDismountTo(level(), at, rider, Pose.STANDING)) {
                    return at;
                }
            }
        }
        return super.getDismountLocationForPassenger(rider);
    }

    /**
     * effects: returns the highest floor under {@code door} that {@code rider} fits on, from a block
     * over the aircraft's own floor down to {@link #GET_OUT} and a block under it, and poses them for
     * it; null if there is none
     */
    @Nullable
    private Vec3 groundUnder(Vec3 door, LivingEntity rider) {
        for (int y = Mth.floor(getY()) + 1; y >= Mth.floor(getY() - GET_OUT) - 1; y--) {
            BlockPos pos = BlockPos.containing(door.x, y, door.z);
            double floor = level().getBlockFloorHeight(pos);
            if (!DismountHelper.isBlockFloorValid(floor)) {
                continue;
            }
            Vec3 spot = new Vec3(door.x, y + floor, door.z);
            for (Pose pose : rider.getDismountPoses()) {
                if (DismountHelper.canDismountTo(level(), spot, rider, pose)) {
                    rider.setPose(pose);
                    return spot;
                }
            }
        }
        return null;
    }

    // --- the hook ------------------------------------------------------------------

    /** effects: returns where the hook is in the world, or null without one: the profile's point turned with the body */
    @Nullable
    public Vec3 hookPoint() {
        AircraftProfile ap = aircraft();
        VehicleProfile p = profile();
        if (ap == null || p == null || ap.hook().isEmpty()) {
            return null;
        }
        return position().add(rotate(p.localBlocks(ap.hook().get())));
    }

    /**
     * effects: on the server, the hook key: with a load on the hook, lets it go if it rests on the
     * ground (otherwise "Set it down first"); with none, takes the nearest free load whose eye is
     * in reach under the hook (otherwise "Nothing under the hook")
     */
    public void hookKey(Player pilot) {
        Vec3 hook = hookPoint();
        if (hook == null) {
            return;
        }
        if (trailer() instanceof SlungLoad load) {
            if (load.resting()) {
                unhitch(load);
            } else {
                pilot.displayClientMessage(Component.translatable("rotorcraft.hook.set_down_first"), true);
            }
            return;
        }
        SlungLoad best = null;
        double nearest = Double.MAX_VALUE;
        for (SlungLoad load : level().getEntitiesOfClass(SlungLoad.class, getBoundingBox().inflate(4.0, 40.0, 4.0), l -> l.tower() == null)) {
            com.chunkworks.rotorcraft.api.SlingProfile sp = load.sling();
            Vec3 eye = load.eyePoint();
            if (sp == null || eye == null || !Sling.inReach(hook.x, hook.y, hook.z, eye.x, eye.y, eye.z, sp.rope())) {
                continue;
            }
            double d = eye.distanceToSqr(hook);
            if (d < nearest) {
                nearest = d;
                best = load;
            }
        }
        if (best == null) {
            pilot.displayClientMessage(Component.translatable("rotorcraft.hook.nothing"), true);
            return;
        }
        best.hooked();
        hitch(best);
    }

    /** effects: lets go of {@code load}, which drops where it hangs */
    void unhitch(SlungLoad load) {
        load.unhitch();
    }

    // --- the crop sprayer ------------------------------------------------------------

    public boolean sprayerFitted() {
        return entityData.get(DATA_SPRAYER);
    }

    public int bonemeal() {
        return entityData.get(DATA_BONEMEAL);
    }

    public boolean spraying() {
        return entityData.get(DATA_SPRAYING);
    }

    /** effects: on the server, the sprayer key: switches spraying on (a sprayer fitted, bone meal in it or a creative pilot) or off, and says which */
    public void sprayKey(Player pilot) {
        if (!sprayerFitted()) {
            pilot.displayClientMessage(Component.translatable("rotorcraft.sprayer.none"), true);
            return;
        }
        if (spraying()) {
            entityData.set(DATA_SPRAYING, false);
            pilot.displayClientMessage(Component.translatable("rotorcraft.sprayer.off"), true);
        } else if (bonemeal() > 0 || pilot.hasInfiniteMaterials()) {
            entityData.set(DATA_SPRAYING, true);
            pilot.displayClientMessage(Component.translatable("rotorcraft.sprayer.on", bonemeal()), true);
        } else {
            pilot.displayClientMessage(Component.translatable("rotorcraft.sprayer.empty"), true);
        }
    }

    /**
     * effects: on the server, a tick of spraying: every column the boom swept since last tick that
     * is due is looked down, within {@link #SPRAY_HEIGHT}; the first block that is not air, if
     * sprayable and the pilot may touch it there, gets one dose through the game's own bone meal
     * (its event, its roll, one bone meal from the tank; none in creative); at most
     * {@link #SPRAY_DOSES} a tick. Only with a pilot and the engine running, in the air. An empty
     * tank switches it off.
     */
    private void spray(VehicleProfile p) {
        AircraftProfile ap = aircraft();
        LivingEntity pilot = getControllingPassenger();
        if (ap == null || ap.sprayer().isEmpty() || !sprayerFitted() || !(pilot instanceof Player player) || !engineCanRun() || standing(clearance(p))) {
            lastBoom = null;
            return;
        }
        if (bonemeal() <= 0 && !player.hasInfiniteMaterials()) {
            entityData.set(DATA_SPRAYING, false);
            player.displayClientMessage(Component.translatable("rotorcraft.sprayer.empty"), true);
            return;
        }
        AircraftProfile.Sprayer s = ap.sprayer().get();
        Vec3 boom = position().add(rotate(p.localBlocks(s.at())));
        double heading = Math.toRadians(getYRot());
        if (lastBoom == null) {
            lastBoom = boom;
            lastBoomHeading = heading;
        }
        boomY = boom.y;
        sprayTick = level().getGameTime();
        doses = 0;
        sprayer = player;
        Swath.sweep(lastBoom.x, lastBoom.z, lastBoomHeading, boom.x, boom.z, heading, s.width(), sprayColumn);
        sprayer = null;
        lastBoom = boom;
        lastBoomHeading = heading;
        if (sprayTick % 100 == 0) {
            dosed.long2LongEntrySet().removeIf(e -> !Swath.due(e.getLongValue(), sprayTick, SPRAY_COOLDOWN));
        }
    }

    /** effects: doses the column at (x, z) if it is due, as {@link #spray} says; marks it dosed either way */
    private void sprayColumn(int x, int z) {
        Player player = sprayer;
        if (player == null || doses >= SPRAY_DOSES) {
            return;
        }
        long key = BlockPos.asLong(x, 0, z);
        if (dosed.containsKey(key) && !Swath.due(dosed.get(key), sprayTick, SPRAY_COOLDOWN)) {
            return;
        }
        dosed.put(key, sprayTick);
        int top = Mth.floor(boomY), bottom = Mth.floor(boomY - SPRAY_HEIGHT);
        for (int y = top; y >= bottom; y--) {
            BlockState state = level().getBlockState(sprayAt.set(x, y, z));
            if (state.isAir()) {
                continue;
            }
            if (state.is(Rotorcraft.SPRAYABLE) && player.mayBuild() && level() instanceof ServerLevel server && server.mayInteract(player, sprayAt)) {
                boolean creative = player.hasInfiniteMaterials();
                if (bonemeal() <= 0 && !creative) {
                    return;
                }
                ItemStack dose = new ItemStack(Items.BONE_MEAL);
                BlockPos at = sprayAt.immutable();
                if (BoneMealItem.applyBonemeal(dose, level(), at, player)) {
                    if (dose.isEmpty() && !creative) {
                        entityData.set(DATA_BONEMEAL, bonemeal() - 1);
                    }
                    level().levelEvent(1505, at, 15);
                    doses++;
                }
            }
            return;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        AircraftProfile ap = aircraft();
        ItemStack held = player.getItemInHand(hand);
        if (ap != null && ap.sprayer().isPresent() && !player.isSecondaryUseActive()) {
            if (held.is(RotorcraftContent.CROP_SPRAYER.get()) && !sprayerFitted()) {
                if (!level().isClientSide()) {
                    entityData.set(DATA_SPRAYER, true);
                    held.consume(1, player);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 0.8f);
                    player.displayClientMessage(Component.translatable("rotorcraft.sprayer.fitted"), true);
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
            int each = held.is(Items.BONE_MEAL) ? 1 : held.is(Items.BONE_BLOCK) ? 9 : 0;
            if (sprayerFitted() && each > 0) {
                if (!level().isClientSide()) {
                    int room = (TANK - bonemeal()) / each;
                    int taken = Math.min(room, held.getCount());
                    if (taken > 0) {
                        entityData.set(DATA_BONEMEAL, bonemeal() + taken * each);
                        held.consume(taken, player);
                        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_MEAL_USE, SoundSource.NEUTRAL, 1.0f, 1.0f);
                    }
                    player.displayClientMessage(Component.translatable("rotorcraft.sprayer.loaded", bonemeal(), TANK), true);
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
        }
        return super.interact(player, hand);
    }

    /** effects: a crouching empty-handed click on the boom takes the sprayer off, its bone meal with it; anything else is Vanilla Wheels' */
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        AircraftProfile ap = aircraft();
        VehicleProfile p = profile();
        if (ap != null && p != null && ap.sprayer().isPresent() && sprayerFitted() && player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty()
                && rotate(p.localBlocks(ap.sprayer().get().at())).distanceTo(hit) <= Math.max(1.5, ap.sprayer().get().width() / 2.0)) {
            if (!level().isClientSide()) {
                takeSprayerOff(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        return super.interactAt(player, hit, hand);
    }

    /** effects: on the server, gives {@code player} the sprayer and its bone meal back, where a give goes, and takes it off */
    private void takeSprayerOff(Player player) {
        int meal = bonemeal();
        entityData.set(DATA_SPRAYER, false);
        entityData.set(DATA_BONEMEAL, 0);
        entityData.set(DATA_SPRAYING, false);
        Carried.giveOrDrop(player, new ItemStack(RotorcraftContent.CROP_SPRAYER.get()));
        while (meal > 0) {
            int n = Math.min(64, meal);
            Carried.giveOrDrop(player, new ItemStack(Items.BONE_MEAL, n));
            meal -= n;
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 1.0f, 0.8f);
        player.displayClientMessage(Component.translatable("rotorcraft.sprayer.removed"), true);
    }

    // --- keeping it ------------------------------------------------------------------

    @Override
    public ItemStack toItem() {
        ItemStack stack = super.toItem();
        if (sprayerFitted()) {
            stack.set(RotorcraftContent.SPRAYER.get(), bonemeal());
        }
        return stack;
    }

    @Override
    public boolean loadFromItem(ItemStack stack) {
        if (!super.loadFromItem(stack)) {
            return false;
        }
        Integer meal = stack.get(RotorcraftContent.SPRAYER.get());
        entityData.set(DATA_SPRAYER, meal != null);
        entityData.set(DATA_BONEMEAL, meal == null ? 0 : Mth.clamp(meal, 0, TANK));
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Sprayer", sprayerFitted());
        tag.putInt("BoneMeal", bonemeal());
        tag.putBoolean("WreckComingDown", wreckComingDown);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_SPRAYER, tag.getBoolean("Sprayer"));
        entityData.set(DATA_BONEMEAL, Mth.clamp(tag.getInt("BoneMeal"), 0, TANK));
        wreckComingDown = tag.getBoolean("WreckComingDown");
        flight = Flight.landed(Math.toRadians(getYRot()));
    }

    /** effects: returns the parts that spin, from the aircraft profile; none without one */
    public List<AircraftProfile.Rotor> rotors() {
        AircraftProfile ap = aircraft();
        return ap == null ? List.of() : ap.rotors();
    }
}
