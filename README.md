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

**1.1.0** is built but not released: the hull, the crash judging, the up, down and get-out keys and
the Shift mixin moved into Vanilla Wheels 1.13.0 (its D-0031), which a second protocol, the
submarines, shares. Nothing changes in flight.

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

The keys are live only aboard an aircraft. Space, Left Shift and R are Vanilla Wheels' *Up*, *Down*
and *Get out* (shared by every vehicle that flies or dives, rebound under Vanilla Wheels in
Controls); G and V are rebound under Rotorcraft.

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
  becomes a packed wreck once it touches down, riders unhurt. Set down again broken, it stays where
  it is put, unflyable, until it is repaired by hand or on the lift (D-0004).
- **Nobody aboard is ever hurt by flying.** No fall reaches a rider; it settles on water or lava
  and nobody goes under; it runs nothing over and breaks no glass or leaves. Riders cannot get
  out more than three blocks up ("Too high to get out").
- **Getting out** puts a rider on the ground beside their own seat, out of that side's door, just
  clear of the body (and of any hull box beside the seat); past a wall there, out of the other
  side's. Over water or a drop, they step out at the door at the aircraft's own height.
- **Crashes wear it.** Meeting a wall or a roof faster than a quarter of a block a tick, or the
  ground faster than three tenths, costs condition, growing with the square of the speed carried
  into it: about a third of a Huey at top speed into a wall, a wreck at one and a half blocks a
  tick. Each crash shows in the server log (`Rotorcraft: <aircraft> at <pos> crashed: <n>
  condition lost`). Repair it as any vehicle: right-click or the lift.
- **Rotor blades** pass through trees and walls without harm, but a spinning rotor strikes
  anything alive its disc touches (the blades' plane and a quarter of a block either side, out to
  the rotor's `radius`, tilted with the body): 10 damage at full rotor speed, less as it spools up
  or winds down, none under a fifth of it, armour counting and the game's knockback away from the
  aircraft. A chicken or a cow dies at once; a player survives one strike. It is blamed on the
  pilot, so the server's PvP rule and teams hold; with nobody at the controls it is nobody's.
  Nothing aboard is ever struck. A Huey's main rotor clears anyone standing or jumping beside it;
  its tail rotor's lowest sweep is at a standing player's head. Deaths read "was cut down by
  <pilot>'s rotor". The body meets the world in the air as its profile's `hull` of boxes, a
  cabin, a boom and a fin each probed all over, so a long tail stops on what is under its middle.

## The sling

Load the **Sling Container** on the ground: open its doors, lead animals (or Serfdom's captives)
in as with the Trailer, and fill its chests. Then hover over it with the hook within reach (the
hook within a block and a half of straight above its ring, no higher than its rope and a block
and a half) and press **G**. It lifts as you climb, swings under you as you fly, and is set down
softly as you hold Shift; held down further, the hook comes to rest just over the load's ring
and no lower. Press **G** again once it rests to let it go: hanging, the hook will not
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
  "rotors": [{"part": {"group": "rotor_main"}, "pivot": [0, 70, 2], "axis": [0, 1, 0], "speed": 1.0,
              "radius": 118}],                  // spun about the pivot; speed: share of the turn, negative the other way; radius: the blades' reach (optional, 0), so a blade alone in view still draws it
  "hook": [0, 2, 0],                            // the cargo hook (optional)
  "sprayer": {"part": {"group": "spray_boom"}, "at": [0, 4, -10], "width": 11},  // a sprayer mount: the boom drawn while fitted, the nozzle line, the swath in blocks (optional)
  "hull": [{"from": [-19, 7, -42], "to": [19, 37, 57]}, ...]   // boxes (two corners) the body meets the world with in the air (optional; absent, the body's nose and tail)
}
```

A long body wants a `hull`: without one, the air's footprint is Vanilla Wheels' nose and tail
points at half the body's length either way of the origin, and the origin is best under the mast,
so the aircraft lands on its skids and turns about its rotor.

A slung load is likewise a Vanilla Wheels profile (no engine, no seats) and
`data/<ns>/rotorcraft/sling_load/<name>.json`: `{"lift": [0, 44, 0], "rope": 4.0}`, its eye
(mesh units) and its rope (blocks, 1 to 32).

## Layout

`src/domain` (the JDK and Vanilla Wheels' pure layer, plain JUnit): `Flight` (the step, `struck`,
and `CRASH`, what a crash costs an aircraft as a Vanilla Wheels `Crash`), `Airframe`, `FlightInput`,
`Sling` (the rope), `Swath` (the boom's columns), `Exit` (the doors a rider gets out of), `Strike`
(what a spinning rotor's disc touches, and how hard). The hull's probe points (`Hull`) are Vanilla
Wheels' since 1.1.0. `src/main`: `api`
(`Rotorcraft`, `AircraftProfile`, `SlingProfile`), `Aircraft` and `SlungLoad` (the entities,
extending Vanilla Wheels' `Vehicle`), `Floors` (fluid surfaces as floor), `RotorcraftContent`,
`net/Payloads` (hook, spray), and `client` (`FlightControls`,
`RotorcraftKeys`, `AircraftRenderer`, `SlungLoadRenderer`, `Mist`, `SprayerIndicator`,
`SprayerSound`). `src/gametest`: the box helicopter, the box boom (a hull of boxes) and the box
crate, 33 gametests, the booth -- a mod of its own, never shipped.

## Building and testing

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH="$JAVA_HOME/bin:$PATH"
./gradlew test               # the pure layer
./gradlew check              # plus the gametests and the booth (needs a display; -PskipBooth)
```

Vanilla Wheels 1.13.0 and Carried come from Maven Local (`./gradlew publishToMavenLocal` in their
repos first). Art and test assets: `uv run --no-project python devtools/art/build.py`.

The booth flies a box helicopter on real key presses, sent through XTEST by
`devtools/booth/xkey.py` (`uv` with `python-xlib`): NeoForge reads Shift from GLFW's own key state,
which a press handed to Minecraft does not move. The helper refuses any display with a window
manager, so run the booth on its own X server (a Xephyr), never on a desktop.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
