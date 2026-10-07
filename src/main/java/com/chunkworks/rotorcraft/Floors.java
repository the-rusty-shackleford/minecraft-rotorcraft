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

import com.chunkworks.vanillawheels.domain.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The floor under an aircraft or a load, as this protocol reads it: the first collision top, or a
 * fluid's surface -- water and lava are floors here, so an aircraft settles on a lake, a load is
 * set down on it, and nobody aboard goes under (D-0001 here). Reads blocks through a caller's
 * mutable position, so a probe allocates nothing.
 */
final class Floors {
    private Floors() {}

    /**
     * effects: returns how far the floor -- the first collision top or fluid surface at or under
     * {@code y} -- lies below {@code y} in the column at (x, z), blocks, at most {@code depth}
     */
    static double below(Level level, BlockPos.MutableBlockPos at, double x, double y, double z, int depth) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(y + 0.01); by >= Mth.floor(y) - depth; by--) {
            BlockState state = level.getBlockState(at.set(bx, by, bz));
            double top = Double.NEGATIVE_INFINITY;
            VoxelShape shape = state.getCollisionShape(level, at);
            if (!shape.isEmpty()) {
                top = by + shape.max(Direction.Axis.Y);
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                top = Math.max(top, by + fluid.getHeight(level, at));
            }
            if (top > Double.NEGATIVE_INFINITY && top <= y + 0.01) {
                return Math.max(0.0, y - top);
            }
        }
        return depth;
    }

    /** effects: returns the surface of the first fluid under (x, y, z) within {@code depth} blocks, through air only; minus infinity if a solid block or nothing comes first */
    static double fluidSurfaceBelow(Level level, BlockPos.MutableBlockPos at, double x, double y, double z, double depth) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(y); by >= Mth.floor(y - depth); by--) {
            FluidState fluid = level.getFluidState(at.set(bx, by, bz));
            if (!fluid.isEmpty()) {
                return by + fluid.getHeight(level, at);
            }
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return Double.NEGATIVE_INFINITY;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** effects: returns {@code delta}, a move from (x, y, z), stopped going down at the surface of a fluid under (x, z) */
    static Vec3 onFluid(Level level, BlockPos.MutableBlockPos at, double x, double y, double z, Vec3 delta) {
        if (delta.y >= 0) {
            return delta;
        }
        double floor = fluidSurfaceBelow(level, at, x, y, z, -delta.y + 1.0);
        return y + delta.y < floor ? new Vec3(delta.x, Math.min(0.0, floor - y), delta.z) : delta;
    }

    /** effects: returns {@code solid}, the ground as Vanilla Wheels' terrain pose reads it, with a fluid's surface read as ground too, heights relative to {@code y} */
    static Terrain.Columns withFluids(Terrain.Columns solid, Level level, double y) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        return (x, z, lo, hi) -> {
            double top = solid.top(x, z, lo, hi);
            int bx = Mth.floor(x), bz = Mth.floor(z);
            for (int by = Mth.floor(y + hi); by >= Mth.floor(y + lo); by--) {
                FluidState fluid = level.getFluidState(at.set(bx, by, bz));
                if (!fluid.isEmpty()) {
                    double surface = by + fluid.getHeight(level, at) - y;
                    return surface >= lo && surface <= hi ? Math.max(top, surface) : top;
                }
                if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                    return top;
                }
            }
            return top;
        };
    }
}
