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
import com.chunkworks.rotorcraft.api.AircraftProfile;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.client.Appearance;
import com.chunkworks.vanillawheels.client.MeshDrawer;
import com.chunkworks.vanillawheels.client.VehicleRenderer;
import com.chunkworks.vanillawheels.domain.Rotation;
import com.chunkworks.vanillawheels.domain.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Draws an aircraft as Vanilla Wheels draws a vehicle, with its rotors cut out of the body and
 * spun -- each about its own axis through its own pivot, at its own share of the rotor's turn --
 * and its crop sprayer's boom drawn only while one is fitted.
 */
public final class AircraftRenderer extends VehicleRenderer {
    public AircraftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected Appearance appearance(Vehicle vehicle, VehicleProfile p) {
        AircraftProfile ap = vehicle instanceof Aircraft a ? a.aircraft() : null;
        return ap == null ? super.appearance(vehicle, p) : Appearance.of(p, ap.extras());
    }

    @Override
    protected void drawExtras(Vehicle vehicle, VehicleProfile p, Appearance a, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, VertexConsumer solid, int light, int overlay) {
        AircraftProfile ap = vehicle instanceof Aircraft aircraft ? aircraft.aircraft() : null;
        if (ap == null || a.extras.size() < ap.extras().size()) {
            return;
        }
        Aircraft aircraft = (Aircraft) vehicle;
        Transform t = p.toLocal();
        double turn = aircraft.rotorAngle(partialTick);
        for (int i = 0; i < ap.rotors().size(); i++) {
            AircraftProfile.Rotor r = ap.rotors().get(i);
            Rotation spin = new Rotation(r.pivot(), r.axis(), turn * r.speed()).mirrored(t);
            poseStack.pushPose();
            rotateAround(poseStack, new Rotation(spin.pivot().times(p.scale()), spin.axis(), spin.radians()));
            MeshDrawer.draw(a.extras.get(i), poseStack.last(), solid, MeshDrawer.WHITE, light, overlay, MeshDrawer.Shading.LIT);
            poseStack.popPose();
        }
        if (ap.sprayer().isPresent() && aircraft.sprayerFitted()) {
            MeshDrawer.draw(a.extras.get(ap.rotors().size()), poseStack.last(), solid, MeshDrawer.WHITE, light, overlay, MeshDrawer.Shading.LIT);
        }
    }
}
