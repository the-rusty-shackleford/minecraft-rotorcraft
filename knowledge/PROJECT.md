---
title: Rotorcraft — project
type: overview
layer: store
tags: [overview]
---

# Rotorcraft

## What this is

The rotorcraft protocol (Rusty, 2026-10-06), layered on Vanilla Wheels 1.12.0 (its D-0030): the
flight, the sling and the crop sprayer; aircraft mods (`minecraft-huey`, `minecraft-chinook`) are
data. The plan is `~/.claude/plans/i-want-to-add-curious-locket.md`. Decisions D-0001 (flight),
D-0002 (sling), D-0003 (sprayer).

## 1.1.0 — released 2026-10-08 in pack 1.78.0 (the shared parts moved into Vanilla Wheels)

nfx's submarines are a second protocol on Vanilla Wheels. Rusty chose (2026-10-07) that what both
need move into Vanilla Wheels 1.13.0 (its D-0031) rather than be copied:
- `domain/Hull`;
- `domain/Wear`, now Vanilla Wheels' `Crash` with this protocol's speeds as `Flight.CRASH`;
- the server's judging of reported moves;
- the Up/Down/Get-out keys with the get-out payload;
- the Shift mixin.

`Aircraft` overrides Vanilla Wheels' hooks (`verticalControls`, `getOut`, `hullPoints`,
`crashes`, `ownChange`, `crashed` with the anvil's sound). G and V are `Keys.RidingKey`s, and act
once a press through Vanilla Wheels' `Keys.Press` (its D-0032): held past the keyboard's repeat
delay, G had caught and let go of a load over and over since 1.0.0, and V switched the sprayer on
and off.

D-0004 rides in it (Rusty, 2026-10-07: a friend's Huey broke and could not be repaired): a broken
aircraft set down from its item stays to be repaired; only a wreck made in the air is packed when it
comes down. Before, it was packed again on its first tick, so nothing could reach it to mend it.

Network "2". Nothing changes in flight:
- 37 JUnit (49, less the 12 that moved);
- the same 33 gametests, and a 34th: a broken aircraft set down from its item stays to be repaired (D-0004);
- the real-key booth's 13 checks, Space and a real Left Shift reaching Vanilla Wheels' keys.

The Huey's and the Chinook's gametests pass on it (their gametest code reads `Keys.UP` and
`Condition.MAX` now). The wiki page says where the keys are rebound.

Released with the submarines on Rusty's go, tag `v1.1.0` at `8b3b867`: the release gate (2026-10-08)
green with 37 JUnit, 34 gametests and the booth's 14 checks; sha1 `cb585cc0` on GitHub and on the
server (the server repo's `knowledge/releases/pack-1.78.0.md`). The Huey 1.1.1 and the Chinook 1.0.1
nest it. Not yet seen on the box: the friend's broken Huey set down and mended.

## Status: 1.0.0 released 2026-10-07 in pack 1.75.0

- Gate: 49 JUnit, 33 GameTests and the booth (13 checks) green; every new rule run against its
  mutation and caught.
- Found in the 4070 playtest (2026-10-07, Rusty: "left shift is not descending"): Descend on Left
  Shift never worked. NeoForge judges a key with no modifier as up while Shift, Control or Alt is
  held in any context but the game's own, and the aircraft keys have their own context: holding
  Shift switched Descend off, and G, V and R with it. The aircraft keys now judge their modifier
  as the game's keys do (`RotorcraftKeys.AircraftKey`), and are let go whenever the player is not
  aboard (a Shift let go after getting out had stayed down into the next boarding). The booth now
  flies a box helicopter on real key presses (`devtools/booth/xkey.py`, XTEST on the booth's own
  display): on the old keys it failed Descend, the hook under Shift and the descent; without the
  let-go, the next boarding. Earlier gates scripted the flight input and could not see it.
- Also from the playtest (Rusty: getting out "put me on top of the helicopter ... It should put me
  on the ground outside of the pilot door"): Vanilla Wheels' rule aims about two blocks from the
  centre the way the rider looks, and looks for a floor only a block under the body's top, so a
  Huey (2.65 tall) found none and fell back to the game's default, its roof. An aircraft now puts
  a rider on the ground out of their own seat's door (`domain/Exit`, JUnit; `Aircraft`'s ground
  search, GameTest: landed, hovering 2.5 up, and past a wall the other side). On the old rule the
  GameTest failed: the box helicopter's pilot came out on the wrong side. Rusty, in the playtest:
  "exiting looks good!"
- The rotors strike (Rusty, the same playtest: "hit a bird in the blades? It gets hurt, possibly
  killed. You jump into the spinning rotors? Same deal"; he passed the proposal as written). A
  spinning rotor's disc (`domain/Strike`: the blades' plane and a quarter block either side, out
  to the profile's `radius`, tilted with the body by Vanilla Wheels' `posed`) strikes anything
  alive it touches: 10 damage at full speed, by the speed, none under a fifth; a `rotorcraft:rotor`
  damage type blamed on the pilot (PvP and teams hold), the game's knockback. Nothing aboard is
  struck; blocks are untouched; a strike costs the aircraft nothing (his call, "All seems fine").
  JUnit on the Huey's real discs (a standing player clears its tail rotor by a hair, a jumping one
  is struck); GameTests on the box boom: a bird in the disc dies, blamed on the pilot, while a cow
  under it and the crew aboard are untouched; a rotor at rest strikes nothing; flying forward, it
  cuts down a bird in its path. Mutations: no strike failed both strike tests; no exemption killed
  the box boom's rider, whose head is through the disc.
- Built: flight, the sling, the crop sprayer (D-0001 to D-0003); the Sling Container and the
  sprayer's icon, recipe and hiss; the hull of boxes and the rotors' reach (D-0001); a landed
  hooked load stops the hook over its eye (D-0002); the booth; the wiki page.
- The aircraft: `minecraft-huey` and `minecraft-chinook` (data only), each with its own booth
  filming the container on its hook and the sprayer working a field.
- Rusty passed the booth photos on 2026-10-07 ("Looks good"). Not done: the playtest on the 4070
  (flight feel is tuned there).
- Released 2026-10-07 in pack 1.75.0 on Rusty's "looks good, fix the latent key bug then release"
  (the server repo's `knowledge/releases/pack-1.75.0.md`): public repo created then, the jar's sha1
  `c1b2dd91` on GitHub and on the server. Not yet seen: anyone flying it on the box. With Vanilla
  Wheels 1.12.0, the Huey and the Chinook.

## Shape

`domain` (JDK-only): `Flight`, `Airframe`, `FlightInput`, `Wear`, `Sling`, `Swath`, `Hull`. `main`:
`Aircraft`/`SlungLoad` extend Vanilla Wheels' `Vehicle` and override its hooks; `api` registries
`rotorcraft:aircraft` and `rotorcraft:sling_load`; keys, payloads, the Player mixin, the
renderers. `gametest`: box helicopter, box boom and box crate (generated by `devtools/art/build.py`),
templates `airfield` (40 cube) and `farm` (24 cube). A gametest template is encased in barrier
blocks: a flight that leaves its 40 blocks crashes into them (three tests did, before their
distances were cut).
