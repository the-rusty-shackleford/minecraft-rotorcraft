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
package com.chunkworks.rotorcraft.mixin;

import com.chunkworks.rotorcraft.Aircraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift is the collective aboard an aircraft, never a way out (Rusty's call, Immersive Aircraft's
 * keys): the game dismounts a rider whose sneak key is down, and a pilot holding it to come down
 * would drop out of the sky. The get-out key lets riders out instead, near the ground only. And a
 * rider holding Shift is not drawn crouching: the crouch would drop the eye and with it the seat,
 * which hangs from the eye.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void rotorcraft$shiftIsTheCollective(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof Aircraft) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updatePlayerPose", at = @At("TAIL"))
    private void rotorcraft$seatedNotCrouching(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.getVehicle() instanceof Aircraft && self.getPose() == Pose.CROUCHING) {
            self.setPose(Pose.STANDING);
        }
    }
}
