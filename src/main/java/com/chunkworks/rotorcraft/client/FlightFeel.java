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
package com.chunkworks.rotorcraft.client;

import com.chunkworks.rotorcraft.domain.Controls;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * How the pilot's keys move the collective and the stick, in {@code config/rotorcraft-client.toml}:
 * the pilot's own, since the pilot's client flies the aircraft and the server bounds every move by
 * the airframe whatever these say. The Mods screen's config page saves it live, so it can be tuned
 * in flight. Holds no client types: registering it on a dedicated server is a no-op.
 */
public final class FlightFeel {
    private FlightFeel() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue LEVER_SECONDS;
    public static final ModConfigSpec.DoubleValue DETENT_SHARE;
    public static final ModConfigSpec.DoubleValue DETENT_SLOWDOWN;
    public static final ModConfigSpec.DoubleValue TILT_SECONDS;
    public static final ModConfigSpec.DoubleValue SPRING_SECONDS;
    public static final ModConfigSpec.BooleanValue STICK_STAYS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("The collective: a lever on Ascend and Descend (Space and Left Shift) that stays where you let it go.",
                        "In its detent at the middle it holds the height; above it climbs and below it descends,",
                        "faster the further from the detent.").push("collective");
        LEVER_SECONDS = builder.comment("Seconds a held key takes to move the lever from the bottom to the top.")
                .defineInRange("sweepSeconds", 1.0, 0.2, 5.0);
        DETENT_SHARE = builder.comment("The detent's width, as a share of the lever's travel.")
                .defineInRange("detentWidth", 0.1, 0.0, 0.4);
        DETENT_SLOWDOWN = builder.comment("How many times slower the lever moves through its detent, so a held key is felt to pass it.")
                .defineInRange("detentSlowdown", 4.0, 1.0, 10.0);
        builder.pop();
        builder.comment("The stick, on Forward and Back (W and S): the longer a key is held, the further the nose tilts",
                        "and the faster it flies that way.").push("stick");
        TILT_SECONDS = builder.comment("Seconds a held key takes to tilt the stick from level to full.")
                .defineInRange("tiltSeconds", 1.5, 0.1, 6.0);
        SPRING_SECONDS = builder.comment("Seconds the stick takes to spring back level from full when let go (unless it stays).")
                .defineInRange("springSeconds", 0.5, 0.05, 3.0);
        STICK_STAYS = builder.comment("Whether the stick stays where you let it go, as the lever does: tilted, it flies on at that",
                        "share of its top speed, and S or W bring it back to level, where it brakes to a hover.",
                        "Off, it springs back level and brakes to a hover whenever you let go.")
                .define("stays", false);
        builder.pop();
        SPEC = builder.build();
    }

    /** The controls as last read from the config; rebuilt when it loads or is saved, never in a tick. */
    private static volatile Controls cached = Controls.DEFAULT;

    /** effects: returns the controls the config says, {@link Controls#DEFAULT} before it loads */
    public static Controls controls() {
        return cached;
    }

    /** effects: rereads the config when it is this mod's and loads or is saved */
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC && SPEC.isLoaded()) {
            cached = Controls.of(LEVER_SECONDS.get(), DETENT_SHARE.get(), DETENT_SLOWDOWN.get(),
                    TILT_SECONDS.get(), SPRING_SECONDS.get(), STICK_STAYS.get());
        }
    }
}
