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
import com.chunkworks.rotorcraft.SlungLoad;
import com.chunkworks.rotorcraft.client.RotorcraftKeys;
import com.chunkworks.rotorcraft.domain.FlightInput;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.client.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The protocol's own equipment on film, in a flat world at noon: the Sling Container parked, from
 * its front quarter and its open back with three cows aboard; then the box helicopter hovering with
 * the box crate hanging on its rope; and the Crop Sprayer item. Then the booth's player flies a box
 * helicopter on real key presses (see {@link #keys}). One {@code booth: PASS} or
 * {@code booth: FAIL} line per check; the Gradle task reads them. Client only, active only under
 * {@code rotorcraft.photobooth}. The helicopters' own booths (the Huey's, the Chinook's) film the
 * container on a hook and the sprayer at work.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class RotorcraftBooth {
    private RotorcraftBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Rotorcraft booth");
    private static final boolean ACTIVE = Boolean.getBoolean("rotorcraft.photobooth");
    
    private static final ResourceLocation CONTAINER = ResourceLocation.fromNamespaceAndPath("rotorcraft", "sling_container");

    private enum Phase { TITLE, LOADING, PLACING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static final int HOLD = 100;
    private static final int SETTLE = 60;
    /** Where the container stands, facing east. */
    private static final double HX = 0.5;
    private static final double HZ = 30.5;
    private static final float EAST = -90.0f;

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static UUID heli;
    private static UUID box;
    private static UUID crateId;
    private static double ground;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
            // Silent from the first tick, before the title music: Rusty listens to music while these run.
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            muted = true;
        }
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.PLACING;
                    mc.options.hideGui = true;
                    steps = plan(mc);
                    onServer(mc, RotorcraftBooth::setUp);
                }
            }
            case PLACING -> {
                if (mc.player != null && entity(mc, box) != null) {
                    phase = Phase.RUNNING;
                    tick = 0;
                }
            }
            case RUNNING -> {
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        LevelSettings settings = new LevelSettings("Rotorcraft booth", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("rotorcraft-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    /** Noon; the Sling Container parked facing east. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000L);
        ground = level.getMinBuildHeight() + 4;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vehicle c = Vehicle.create(level, CONTAINER, new Vec3(HX, ground, HZ), EAST);
        if (!(c instanceof SlungLoad load)) {
            LOG.error("booth: FAIL the Sling Container is a slung load -- {}", c);
            throw new IllegalStateException("no container");
        }
        level.addFreshEntity(load);
        box = load.getUUID();
        aim(sp, load.position().add(5.0, 3.0, -5.0), load.position().add(0.0, 1.3, 0.0));
    }

    /** effects: puts the booth's player at {@code from}, looking at {@code at} */
    private static void aim(ServerPlayer sp, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        sp.teleportTo(sp.serverLevel(), from.x, from.y - sp.getEyeHeight(), from.z, yaw, pitch);
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = HOLD;
        s.add(new Step(t, () -> {
            int green = count(mc, RotorcraftBooth::olive);
            shoot(mc, "booth-container");
            verdict("the Sling Container stands in its factory green", () -> green > 8000 ? null : "green pixels " + green);
            // Its doors open, three cows aboard, from behind and to the left.
            onServer(mc, sp -> {
                if (!(sp.serverLevel().getEntity(box) instanceof SlungLoad load)) {
                    LOG.error("booth: FAIL the container is in the level -- gone");
                    return;
                }
                load.toggleDoors();
                for (int i = 0; i < 3; i++) {
                    var cow = EntityType.COW.create(sp.serverLevel());
                    if (cow != null) {
                        cow.setPos(load.getX(), load.getY(), load.getZ());
                        cow.setNoAi(true);
                        sp.serverLevel().addFreshEntity(cow);
                        if (!cow.startRiding(load, true)) {
                            LOG.error("booth: FAIL a cow boards the container");
                        }
                    }
                }
                Vec3 back = load.position().add(load.rotate(new com.chunkworks.vanillawheels.domain.Vec(0.0, 1.3, -2.2)));
                aim(sp, back.add(-3.0, 1.6, -4.2), back);
            });
        }));
        s.add(new Step(t += SETTLE, () -> {
            SlungLoad load = entity(mc, box) instanceof SlungLoad l ? l : null;
            shoot(mc, "booth-container-open");
            verdict("its doors are open", () -> load != null && load.doorSwing(1.0f) > 0.9f ? null : "swing " + (load == null ? null : load.doorSwing(1.0f)));
            verdict("with three cows aboard", () -> load != null && load.animals().size() == 3 ? null : "animals " + (load == null ? null : load.animals().size()));
        }));
        // The box helicopter, hovering, the box crate on its rope.
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            ServerLevel level = sp.serverLevel();
            Vehicle v = Vehicle.create(level, Rigs.BOX_HELI, new Vec3(HX + 20.0, ground + 6.0, HZ), EAST);
            Vehicle c = Vehicle.create(level, Rigs.BOX_CRATE, new Vec3(HX + 20.0, ground, HZ), EAST);
            if (!(v instanceof Aircraft a) || !(c instanceof SlungLoad crate)) {
                LOG.error("booth: FAIL the box helicopter and crate are an aircraft and a slung load");
                return;
            }
            a.setFuel(a.tank().capacity());
            level.addFreshEntity(a);
            level.addFreshEntity(crate);
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand != null) {
                stand.setInvisible(true);
                stand.setPos(a.getX(), a.getY(), a.getZ());
                level.addFreshEntity(stand);
                stand.startRiding(a, true);
            }
            a.setScriptedFlight(new FlightInput(0, 0, 0, true, false, 0.0));
            heli = a.getUUID();
            crateId = crate.getUUID();
        })));
        s.add(new Step(t += 80, () -> onServer(mc, sp -> {
            if (sp.serverLevel().getEntity(heli) instanceof Aircraft a) {
                a.setPos(a.getX(), ground + 3.6, a.getZ());
                a.hookKey(sp);
                a.setScriptedFlight(new FlightInput(0, 0, 1, true, false, 0.0));
            }
        })));
        s.add(new Step(t += 20, () -> onServer(mc, sp -> {
            if (sp.serverLevel().getEntity(heli) instanceof Aircraft a) {
                a.setScriptedFlight(new FlightInput(0, 0, 0, true, false, 0.0));
                aim(sp, a.position().add(-1.0, -1.5, -9.5), a.position().add(0.0, -1.5, 0.0));
            }
        })));
        s.add(new Step(t += SETTLE, () -> {
            SlungLoad crate = entity(mc, crateId) instanceof SlungLoad l ? l : null;
            shoot(mc, "booth-box-sling");
            verdict("the box crate hangs from the box helicopter's hook", () -> crate != null && crate.tower() != null && crate.getY() > ground + 0.5
                    ? null : "crate " + crate + (crate == null ? "" : ", tower " + crate.tower()));
        }));
        t = keys(mc, s, t + 20);
        s.add(new Step(t += 20, () -> {
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    // --- the keys, pressed for real -------------------------------------------

    private static UUID flown;
    private static double hover;
    private static Process shiftDown;

    /**
     * The booth's player flies a box helicopter on real key presses (devtools/booth/xkey.py): Space
     * climbs it, it holds its height with no key held, held Left Shift brings it down and leaves G
     * working, and a Shift let go after getting out is not still held on boarding again. Real
     * presses, because NeoForge reads Shift from GLFW's own key state, which nothing else moves.
     * effects: adds the steps from {@code t}; returns the last step's tick
     */
    private static int keys(Minecraft mc, List<Step> s, int t) {
        s.add(new Step(t, () -> onServer(mc, sp -> {
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
            ServerLevel level = sp.serverLevel();
            if (!(Vehicle.create(level, Rigs.BOX_HELI, new Vec3(HX + 40.0, ground, HZ), EAST) instanceof Aircraft a)) {
                LOG.error("booth: FAIL the box helicopter is an aircraft");
                return;
            }
            a.setFuel(a.tank().capacity());
            level.addFreshEntity(a);
            sp.startRiding(a, true);
            flown = a.getUUID();
        })));
        s.add(new Step(t += 60, () -> {
            verdict("the booth's player flies the box helicopter", () -> client(mc, flown) instanceof Aircraft a
                    && a.getControllingPassenger() == mc.player ? null : "riding " + (mc.player == null ? null : mc.player.getVehicle()));
            verdict("the boarding line names the get-out key, not Shift", () -> namesGetOut(mc));
            xkey("down", "space");
        }));
        s.add(new Step(t += 50, () -> xkey("up", "space")));
        s.add(new Step(t += 40, () -> {
            Aircraft a = client(mc, flown);
            hover = a == null ? Double.NaN : a.getY();
            verdict("a real Space climbs it", () -> hover > ground + 3.0 ? null : "height " + (hover - ground));
        }));
        s.add(new Step(t += 40, () -> {
            Aircraft a = client(mc, flown);
            double y = a == null ? Double.NaN : a.getY();
            verdict("it holds its height with no key held", () -> Math.abs(y - hover) < 0.3 ? null : "moved " + (y - hover));
            hover = y;
            shiftDown = xkey("down", "Shift_L");
        }));
        s.add(new Step(t += 40, () -> {
            verdict("the key helper pressed Left Shift", () -> shiftDown != null && !shiftDown.isAlive() && shiftDown.exitValue() == 0
                    ? null : "helper " + (shiftDown == null ? "not started" : shiftDown.isAlive() ? "still running" : "exit " + shiftDown.exitValue()));
            verdict("a real Left Shift reaches the game window", () -> InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                    ? null : "GLFW reads Left Shift up");
            verdict("Descend is down while Left Shift is held", () -> Keys.DOWN.isDown() ? null : "Descend reads up");
            xkey("down", "g");
        }));
        s.add(new Step(t += 40, () -> {
            Aircraft a = client(mc, flown);
            double y = a == null ? Double.NaN : a.getY();
            verdict("the hook key works with Left Shift held", () -> RotorcraftKeys.HOOK.isDown() ? null : "Hook reads up");
            verdict("it comes down while Left Shift is held", () -> y < hover - 1.0 ? null : "moved " + (y - hover));
            xkey("up", "g");
            // Out with Shift still held (the server's dismount: R works only near the ground).
            onServer(mc, ServerPlayer::stopRiding);
        }));
        s.add(new Step(t += 20, () -> xkey("up", "Shift_L")));
        s.add(new Step(t += 40, () -> onServer(mc, sp -> {
            if (sp.serverLevel().getEntity(flown) instanceof Aircraft a) {
                sp.startRiding(a, true);
            }
        })));
        s.add(new Step(t += 20, () -> {
            verdict("aboard again, a Left Shift let go off the aircraft is not still down", () -> client(mc, flown) instanceof Aircraft a
                    && a.getControllingPassenger() == mc.player && !Keys.DOWN.isDown()
                    ? null : "aboard " + (mc.player == null ? null : mc.player.getVehicle()) + ", Descend down " + Keys.DOWN.isDown());
            onServer(mc, ServerPlayer::stopRiding);
        }));
        return t;
    }

    /**
     * effects: starts devtools/booth/xkey.py pressing ({@code down}) or letting go ({@code up}) of
     * {@code keysym} on this client's display, and returns it; null, with a FAIL line, if it would
     * not start. The helper refuses a display with a window manager, so it never types on a desktop.
     */
    @org.jetbrains.annotations.Nullable
    private static Process xkey(String action, String keysym) {
        Path script = Path.of(System.getProperty("user.dir"), "..", "..", "devtools", "booth", "xkey.py").normalize();
        Path uv = Path.of(System.getProperty("user.home"), ".local", "bin", "uv");
        try {
            return new ProcessBuilder(Files.isExecutable(uv) ? uv.toString() : "uv", "run", "--no-project", "--with", "python-xlib",
                    "python", script.toString(), action, keysym).inheritIO().start();
        } catch (IOException e) {
            LOG.error("booth: FAIL the key helper starts ({} {}) -- {}", action, keysym, e.toString());
            return null;
        }
    }

    // --- reading the frame -----------------------------------------------

    /** effects: a factory-green pixel in daylight: green over blue, red near green, not bright */
    private static boolean olive(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return g > 30 && g < 140 && g > b + 8 && Math.abs(r - g) < 20;
    }

    /** effects: returns how many pixels of the frame's middle (rows 15..85 %, columns 10..90 %) satisfy {@code test} */
    private static int count(Minecraft mc, IntPredicate test) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int n = 0;
            int w = image.getWidth(), h = image.getHeight();
            for (int y = (int) (h * .15); y < (int) (h * .85); y++) {
                for (int x = (int) (w * .1); x < (int) (w * .9); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int rgb = (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    if (test.test(rgb)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    // --- plumbing --------------------------------------------------------

    /**
     * effects: null if the line the game showed on boarding names the get-out key, else what it
     * said; the game's own line names Shift, which takes an aircraft down (Vanilla Wheels' D-0031)
     */
    @org.jetbrains.annotations.Nullable
    private static String namesGetOut(Minecraft mc) {
        String want = net.minecraft.network.chat.Component.translatable("mount.onboard", Keys.GET_OUT.getTranslatedKeyMessage()).getString();
        String line;
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
            f.setAccessible(true);
            line = f.get(mc.gui) instanceof net.minecraft.network.chat.Component c ? c.getString() : null;
        } catch (ReflectiveOperationException e) {
            line = "unreadable: " + e;
        }
        return want.equals(line) ? null : "line \"" + line + "\", want \"" + want + "\"";
    }

    /** effects: returns the client's aircraft of {@code id}, or null */
    @org.jetbrains.annotations.Nullable
    private static Aircraft client(Minecraft mc, UUID id) {
        return entity(mc, id) instanceof Aircraft a ? a : null;
    }

    /** effects: returns the client's entity of {@code id}, or null */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.world.entity.Entity entity(Minecraft mc, UUID id) {
        if (mc.level == null || id == null) {
            return null;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}
