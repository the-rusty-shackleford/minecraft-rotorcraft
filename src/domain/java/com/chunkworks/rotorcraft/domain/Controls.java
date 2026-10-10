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
package com.chunkworks.rotorcraft.domain;

/**
 * How the pilot's keys move the collective lever and the stick (Rusty, 2026-10-10): the lever's
 * travel a tick with its key held, the half-width of its detent at the middle and its travel a tick
 * through it; the stick's tilt gained a tick with its key held and shed a tick let go; and whether
 * the stick stays where it is let go, as the lever does (a cruise: tilted, it flies on at its share
 * of top speed, with a detent at level of its own), or springs back level (let go, it brakes to a
 * hover). Travel is a share of the lever's (0..1) or the stick's (-1..1) a tick. The pilot's own: a
 * client config, tuned in a playtest; the server bounds every reported move by the airframe's rates
 * whatever these are.
 *
 * <p>RI: every number finite; 0 < leverRate <= 1; 0 <= detent < 0.5; 0 < detentRate <= leverRate;
 *     0 < stickRate <= 1; 0 < stickSpring <= 1.
 * AF: the meaning of each number is its javadoc.
 *
 * @param leverRate   the collective's travel a tick with its key held, outside the detent
 * @param detent      half the detent's width: within this of {@link Flight#HOVER} the lever holds the height
 * @param detentRate  the collective's travel a tick through the detent, so a held key is felt to pass it
 * @param stickRate   the stick's tilt gained a tick with its key held
 * @param stickSpring the stick's tilt shed a tick let go, or held against its tilt, where it springs back
 * @param stickStays  whether the stick stays where it is let go, as the lever does, instead of springing back
 */
public record Controls(double leverRate, double detent, double detentRate, double stickRate, double stickSpring, boolean stickStays) {

    /** As agreed before the playtest: a full sweep of the lever in a second, a tenth of its travel the detent, a quarter speed through it; full tilt in a second and a half, back level in half a second. */
    public static final Controls DEFAULT = new Controls(0.05, 0.05, 0.0125, 1.0 / 30.0, 0.1, false);

    public Controls {
        require(Double.isFinite(leverRate) && leverRate > 0 && leverRate <= 1, "leverRate must be 0..1");
        require(Double.isFinite(detent) && detent >= 0 && detent < 0.5, "detent must be 0..0.5");
        require(Double.isFinite(detentRate) && detentRate > 0 && detentRate <= leverRate, "detentRate must be 0..leverRate");
        require(Double.isFinite(stickRate) && stickRate > 0 && stickRate <= 1, "stickRate must be 0..1");
        require(Double.isFinite(stickSpring) && stickSpring > 0 && stickSpring <= 1, "stickSpring must be 0..1");
    }

    /**
     * requires: every time positive and finite; 0 <= detentShare < 1; slowdown >= 1
     * effects: returns the controls for a lever that sweeps its travel in {@code leverSeconds}, whose
     *     detent is {@code detentShare} of its travel and is passed {@code slowdown} times slower, a
     *     stick that tilts fully in {@code tiltSeconds} and springs back level from full in
     *     {@code springSeconds}, staying where let go if {@code stickStays}: a config's numbers
     * throws: IllegalArgumentException otherwise
     */
    public static Controls of(double leverSeconds, double detentShare, double slowdown, double tiltSeconds, double springSeconds, boolean stickStays) {
        require(leverSeconds > 0 && tiltSeconds > 0 && springSeconds > 0 && Double.isFinite(leverSeconds + tiltSeconds + springSeconds), "times are positive");
        require(detentShare >= 0 && detentShare < 1 && slowdown >= 1 && Double.isFinite(slowdown), "a detent share of 0..1 and a slowdown of 1 or more");
        double lever = Math.min(1.0, 1.0 / (leverSeconds * 20.0));
        return new Controls(lever, detentShare / 2.0, lever / slowdown, Math.min(1.0, 1.0 / (tiltSeconds * 20.0)),
                Math.min(1.0, 1.0 / (springSeconds * 20.0)), stickStays);
    }

    /** effects: returns the half-width of the stick's detent at level, where it stays put: the lever's, over the stick's twice-as-long travel */
    public double stickDetent() {
        return detent * 2.0;
    }

    /** effects: returns the stick's travel a tick through its detent, slowed as the lever's is */
    public double stickDetentRate() {
        return stickRate * detentRate / leverRate;
    }

    private static void require(boolean ok, String what) {
        if (!ok) {
            throw new IllegalArgumentException(what);
        }
    }
}
