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
/**
 * The pure layer: how a rotorcraft moves under its pilot ({@link Flight}, {@link Airframe},
 * {@link FlightInput}), what a crash costs it ({@link Wear}), how a slung load swings on its rope
 * ({@link Sling}), which columns a spray boom sweeps ({@link Swath}). Compiled against the JDK
 * alone, so a Minecraft import here is a compile error.
 */
package com.chunkworks.rotorcraft.domain;
