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
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every game object the protocol registers: the two entity types that make its vehicles (an
 * aircraft, a slung load), the crop sprayer, the component a packed aircraft keeps its sprayer in,
 * the sprayer's hiss. The entity types' own sizes are placeholders: a vehicle sizes itself from
 * its profile, as every Vanilla Wheels vehicle does.
 */
public final class RotorcraftContent {
    private RotorcraftContent() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Rotorcraft.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Rotorcraft.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Rotorcraft.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Rotorcraft.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Rotorcraft.MOD_ID);

    /** Every aircraft: which one, its Vanilla Wheels profile says; how it flies, its aircraft profile. Seen from far off. */
    public static final DeferredHolder<EntityType<?>, EntityType<Aircraft>> AIRCRAFT = ENTITIES.register("aircraft",
            () -> EntityType.Builder.<Aircraft>of(Aircraft::new, MobCategory.MISC)
                    .sized(1.5f, 1.0f)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("aircraft"));

    /** Every slung load. */
    public static final DeferredHolder<EntityType<?>, EntityType<SlungLoad>> SLUNG_LOAD = ENTITIES.register("slung_load",
            () -> EntityType.Builder.<SlungLoad>of(SlungLoad::new, MobCategory.MISC)
                    .sized(1.5f, 1.0f)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("slung_load"));

    /** A packed aircraft's crop sprayer: present when one is fitted, the bone meal in its tank. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPRAYER = COMPONENTS.register("sprayer",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, Aircraft.TANK)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    /** The crop sprayer: fitted to an aircraft with a mount, it doses the crops under its boom with bone meal. */
    public static final DeferredItem<Item> CROP_SPRAYER = ITEMS.registerSimpleItem("crop_sprayer", new Item.Properties().stacksTo(1));

    /** The sprayer's hiss, while it sprays. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRAYER_HISS = SOUNDS.register("sprayer",
            () -> SoundEvent.createVariableRangeEvent(Rotorcraft.id("sprayer")));

    /** A spinning rotor's strike (data/rotorcraft/damage_type/rotor.json): blamed on the pilot, its knockback away from the aircraft. */
    public static final ResourceKey<DamageType> ROTOR_STRIKE = ResourceKey.create(Registries.DAMAGE_TYPE, Rotorcraft.id("rotor"));

    /** effects: registers everything on {@code modBus} */
    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
        TABS.register(modBus);
    }

    /**
     * The protocol's own creative tab (Rusty, 2026-10-10: a pane for every mod with recipes): every
     * aircraft, then every slung load, each with its chassis, then the Crop Sprayer. Vanilla Wheels
     * leaves them out of its own (its D-0034).
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rotorcraft"))
            .icon(() -> CROP_SPRAYER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                List<ResourceLocation> all = parameters.holders().lookupOrThrow(VanillaWheels.VEHICLES).listElements()
                        .map(holder -> holder.key().location())
                        .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
                List<ResourceLocation> ours = new ArrayList<>();
                all.stream().filter(id -> Rotorcraft.aircraft(parameters.holders(), id).isPresent()).forEach(ours::add);
                all.stream().filter(id -> Rotorcraft.slingLoad(parameters.holders(), id).isPresent()).forEach(ours::add);
                ours.forEach(id -> output.accept(ModContent.vehicleStack(id)));
                ours.forEach(id -> output.accept(ModContent.chassisStack(id)));
                output.accept(CROP_SPRAYER.get());
            })
            .build());

    /** The sprayer in Tools too. */
    static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CROP_SPRAYER);
        }
    }
}
