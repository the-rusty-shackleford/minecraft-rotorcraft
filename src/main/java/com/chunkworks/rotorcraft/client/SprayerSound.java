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

import com.chunkworks.rotorcraft.Aircraft;
import com.chunkworks.rotorcraft.RotorcraftContent;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** The crop sprayer's hiss, a loop on every client, heard while its aircraft sprays and eased in and out. */
public final class SprayerSound extends AbstractTickableSoundInstance {
    private final Aircraft aircraft;

    public SprayerSound(Aircraft aircraft) {
        super(RotorcraftContent.SPRAYER_HISS.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.aircraft = aircraft;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.x = aircraft.getX();
        this.y = aircraft.getY();
        this.z = aircraft.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (aircraft.isRemoved()) {
            stop();
            return;
        }
        x = aircraft.getX();
        y = aircraft.getY();
        z = aircraft.getZ();
        volume = Mth.approach(volume, aircraft.spraying() ? 0.5f : 0.0f, 0.05f);
    }
}
