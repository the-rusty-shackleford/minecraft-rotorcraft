# Rotorcraft

A rotorcraft protocol for NeoForge 1.21.1, layered on
[Vanilla Wheels](https://github.com/the-rusty-shackleford/minecraft-vanilla-wheels). An aircraft
is a Vanilla Wheels vehicle (its look, seats, chests, cargo, paint, fuel, keys, condition, and the
Mechanic Lift that builds and paints it) that flies. This mod owns the flying, the rule that
nobody aboard is ever hurt by it while a crash wears the aircraft, the sling hook and the loads it
carries, and the crop sprayer. Aircraft mods ship data and assets; the Huey and the Chinook are
the first.

It ships two things of its own: the **Sling Container**, the standard load, and the **Crop
Sprayer**.

## Flying

| Key (default) | Does |
|---|---|
| W / S | stick: fly forward / back; let go and it brakes to a hover |
| A / D | pedals: turn |
| Space | climb |
| Left Shift | descend (never gets you out, as in Immersive Aircraft) |
| R | get out: only within three blocks of the ground |
| G | hook a load, or let a resting one go |
| V | crop sprayer on / off |
| H | lights (Vanilla Wheels') |

The keys are live only aboard an aircraft and can be rebound under Rotorcraft in Controls.

- **Spool-up.** Boarding as pilot starts the engine; the rotor spools up (three seconds on the
  Huey) before it will climb, and winds down when the pilot gets out. It burns fuel whenever the
  rotor is driven, hovering included; a creative pilot burns none.
- **Hover.** With the collective let go, it holds its height. Let go of the stick and it brakes
  to a hover.
- **Landing.** Hold Shift all the way down: the descent slows by itself near the ground, never
  meeting it faster than a soft landing, from any height. With a load on the hook it slows for
  the load instead, so the load is set down as gently.
- **Engine out.** Out of fuel in the air, or worn to nothing, it autorotates down at a quarter
  of a block a tick, still steering, and lands softly. A wreck in the air is not destroyed: it
  becomes a packed wreck once it touches down, riders unhurt.
- **Nobody aboard is ever hurt by flying.** No fall reaches a rider; it settles on water or lava
  and nobody goes under; it runs nothing over and breaks no glass or leaves. Riders cannot get
  out more than three blocks up ("Too high to get out").
- **Crashes wear it.** Meeting a wall or a roof faster than a quarter of a block a tick, or the
  ground faster than three tenths, costs condition, growing with the square of the speed carried
  into it: about a third of a Huey at top speed into a wall, a wreck at one and a half blocks a
  tick. Each crash shows in the server log (`Rotorcraft: <aircraft> at <pos> crashed: <n>
  condition lost`). Repair it as any vehicle: right-click or the lift.
- **Rotor blades** are drawn only; they pass through trees and walls without harm.

## The sling

Load the **Sling Container** on the ground: open its doors, lead animals (or Serfdom's captives)
in as with the Trailer, and fill its chests. Then hover over it with the hook within reach (the
hook within a block and a half of straight above its ring, no higher than its rope and a block
and a half) and press **G**. It lifts as you climb, swings under you as you fly, and is set down
softly as you hold Shift. Press **G** again once it rests to let it go: hanging, the hook will not
let go ("Set the load down first"). On the ground, a crouching empty-handed click on its ring also
lets it go. A load that catches under something (an overhang, a roof) and is held a block and a
half past its rope lets go, worn by what it hit. Nothing in it is hurt; it rests on water as on
ground. The hook link survives a restart, and a key's recall brings the load with its aircraft.

## The Crop Sprayer

Craft it, then right-click an aircraft with it to fit it (the Huey and the Chinook take one;
crouch and right-click the boom empty-handed to take it off, its bone meal handed back). Right-
click with bone meal (or bone blocks, nine each) to fill its tank, up to 256; the amount shows
beside the hotbar. Fly over a field within twelve blocks of the crops and press **V**: every crop
the boom passes over gets one dose of bone meal, as if you clicked it, at one bone meal a dose,
whatever the speed. A column is dosed once every two seconds, so hovering does not empty the
tank on one row. Only crops (`#rotorcraft:sprayable`, the game's `#minecraft:crops` unless a
datapack adds more): grass and flowers beside the field are left alone. A claim or protection mod
that refuses bone meal refuses the spray too, and nothing is spent there. An empty tank switches
it off; a creative pilot sprays from an empty one for free.

## For aircraft mods: the contract

An aircraft is two files with the same id: Vanilla Wheels' profile
(`data/<ns>/vanillawheels/vehicle/<name>.json`: what it is) and this protocol's
(`data/<ns>/rotorcraft/aircraft/<name>.json`: how it flies). The Vanilla Wheels profile has an
`engine` (whose `max_speed`, `reverse_speed`, `acceleration` and `brake` are its horizontal
flight), a driver's seat and fuel; skids are `"wheels": {"drawn": false, ...}` (contact points,
no wheel mesh, the lift asks no wheels); `sounds.engine` may name a loop in the mod's own
`sounds.json`. Vectors are that profile's: mesh units, its scale and hand.

```jsonc
{
  "climb_rate": 0.4, "descent_rate": 0.5,       // blocks a tick
  "vertical_acceleration": 0.03,                // optional, 0.03
  "yaw_rate": 3.0,                              // degrees a tick at full pedal (optional, 3)
  "spool_ticks": 60,                            // rotor spin-up (optional, 60)
  "tilt": 12,                                   // most nose-down / bank, degrees (optional, 12)
  "rotors": [{"part": {"group": "rotor_main"}, "pivot": [0, 70, 2], "axis": [0, 1, 0], "speed": 1.0}],   // spun about the pivot; speed: share of the turn, negative the other way
  "hook": [0, 2, 0],                            // the cargo hook (optional)
  "sprayer": {"part": {"group": "spray_boom"}, "at": [0, 4, -10], "width": 11}   // a sprayer mount: the boom drawn while fitted, the nozzle line, the swath in blocks (optional)
}
```

A slung load is likewise a Vanilla Wheels profile (no engine, no seats) and
`data/<ns>/rotorcraft/sling_load/<name>.json`: `{"lift": [0, 44, 0], "rope": 4.0}`, its eye
(mesh units) and its rope (blocks, 1 to 32).

## Layout

`src/domain` (JDK only, plain JUnit): `Flight` (the step, `struck`), `Airframe`, `FlightInput`,
`Wear` (what a crash costs), `Sling` (the rope), `Swath` (the boom's columns). `src/main`: `api`
(`Rotorcraft`, `AircraftProfile`, `SlingProfile`), `Aircraft` and `SlungLoad` (the entities,
extending Vanilla Wheels' `Vehicle`), `Floors` (fluid surfaces as floor), `RotorcraftContent`,
`net/Payloads` (get out, hook, spray), `mixin/PlayerMixin`, and `client` (`FlightControls`,
`RotorcraftKeys`, `AircraftRenderer`, `SlungLoadRenderer`, `Mist`, `SprayerIndicator`,
`SprayerSound`). `src/gametest`: the box helicopter and box crate, 25 gametests, the booth -- a
mod of its own, never shipped.

## Building and testing

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH="$JAVA_HOME/bin:$PATH"
./gradlew test               # the pure layer
./gradlew check              # plus the gametests and the booth (needs a display; -PskipBooth)
```

Vanilla Wheels 1.12.0 and Carried come from Maven Local (`./gradlew publishToMavenLocal` in their
repos first). Art and test assets: `uv run --no-project python devtools/art/build.py`.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
