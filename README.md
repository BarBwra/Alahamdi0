# MlumInventory — Minecraft 1.20.1 / Forge 47.4.10

**v3.12.1** (network protocol 15) — see "What is new" below, and `HANDOFF.md` §5.4 and §11b
for developers.

---

## What is new in 3.11.0 – 3.12.1

| | |
| --- | --- |
| **Creative** | only an OP can be in creative. Staff ranks cannot grant it, and a non-OP found in creative is put back in survival |
| **Staff ranks** | the OP makes ranks in game (K → ranks), any name and colour, and ticks what each may do. Any command on the server, from any mod, can be given as `cmd.<command>` |
| **Admin panel (K)** | players (teleport, bring, open inventory, spectate), vanish, restore a death, warn / mute / jail / kick / ban, tickets, money and item alerts, scheduled restart and scheduled commands |
| **Commands** | `/mlum staff ...` (OP), `/mlum ticket <text>` (anyone) |
| **Screens** | ESC menu, title screen, loading screens and crafting table redrawn in the bag's look, under the name Mlum. Client config `[screens]` |
| **F5** | over-the-shoulder camera, X swaps the shoulder, a mark shows where a gun is really aimed |
| **Sounds** | hover, click, open and close sounds in every menu |
| **Low health** | red edges, a heartbeat and muffled sound below 35% health |

Each feel feature can be turned off in the client config `[feel]`.

---

## What is new in 3.10.0

| | |
| --- | --- |
| **Search** | one click (no holding), 1.5 s; Shift 0.75 s with a 50% noise chance; left click cancels. Both hands come up and dig while searching or reviving, with a new spinner |
| **إيد خفيفة** | new skill: the fast search's noise chance 40 / 30 / 20% |
| **تعبئة أسرع** | now 15 / 30 / 45% |
| **Downed** | 360 s, a real ring with M:SS inside, "مصاب" in red, E calls for help, holding F turns the ring red, drag removed |
| **Death bag** | dying from the ground leaves everything in a backpack sized to the loot (never the VIP one) |
| **Loot a body** | their character with their gear on the left, their inventory under it |
| **HUD** | static belt, your face on the wrist device, everything 15% bigger (`fieldHudScale`) |
| **Marker mouse** | redrawn, bigger, green right button |
| **Bag** | gun details show the ammo it takes; quick access runs 3 → 9 left to right |
| **Ghillie suits** | full green suit in plants/leaves or snow suit in snow/ice: invisible, armour and all |

Old server configs are brought up to date once, automatically (`configVersion`) — values you changed
yourself are left alone.

---

## What is new in 3.9.0

| | |
| --- | --- |
| **Field HUD** | wrist device (health inside the armour shield, food, XP, ECG), belt carousel, weapon slab, compass. Client config `fieldHud` turns it off and brings vanilla back |
| **Loot markers** | corner marks and a mouse icon on containers in view; brighter when you look at one in reach; grey once searched |
| **Timed search** | right-click = 2 s search. Hold Shift = 0.25 s but a 25% chance of a noise that pauses you and draws zombies. Moving or getting hit cancels |
| **Downed** | lethal damage puts you on the ground for 180 s. Zombies ignore you. F: tap to call your faction, hold to give up |
| **Reviving** | look at a body, mouse wheel picks loot / revive / drag, F acts. Revive is a 10 s hold. Defib item = instant (uses charge), oxygen item = 5 s hold. Both are placeholder items in config for now |
| **Scout** | the same markers through walls, 8/12/16 blocks; level 3 shows which are empty |
| **تعبئة أسرع** | replaces تعشيق أكثر (same id, levels kept): reloads 20 / 50 / 100% faster |

Test commands: `/mlum downed dummy`, `/mlum downed self`, `/mlum downed revive <player>`,
`/mlum downed charge`, `/mlum downed list`.

---

## What is new in 3.3.0

### The TACZ tooltip regression, and what actually caused it

**Hovering a gun showed its name and nothing else.** No ammo type, no magazine count, no damage.

The cause was the Arabic fix from 3.1.0. TACZ does not put any of that in the tooltip's *text*: it
ships a `GunTooltip`, a `TooltipComponent`, which reaches a screen through
`ItemStack.getTooltipImage()` and is drawn by its own `ClientGunTooltip`. 3.0.0 called vanilla's
`renderTooltip`, which passes that component through, so it worked. 3.1.0 replaced that call with a
hand-rolled renderer that read `getTooltipLines` and drew the strings — and the component channel,
carrying the entire body of every gun tooltip, was silently dropped. Colours went with it, because
every line went through `Component.getString()`.

Tooltips are now assembled as real `ClientTooltipComponent`s, exactly as vanilla builds them, so
another mod's tooltip renders the way its author intended. The bidi problem is dodged **per line**
rather than by avoiding vanilla wholesale:

* a line containing Arabic — raw or pre-baked, `ArabicText.containsArabic` catches both — is shaped
  once here and wrapped directly, so `getVisualOrderText` never touches it and nothing is reordered
  twice;
* every other line goes through `Font.split`, which wraps properly and keeps formatting. Bidi is a
  no-op on text with no right-to-left characters in it.

### The screen

| | before | now |
| --- | --- | --- |
| `محيط` scanner | 182px wide, an 11-row name list | **124px wide, a 4×12 icon grid — 48 slots** |
| `الحالة` status | a 276×146 panel for three numbers | a **46px strip** of three gauges |
| firearm cards | 89px wide, bottom left | **135px** wide, bottom right |
| player model | scale 62 | scale 72, in a column 58px wider |

The scanner stopped being a list. It was spending 182px of width to show an 18px icon next to a
name and still ran out after 11 items; the same column as a grid holds **48**, because the name was
the expensive part and the name is what a tooltip is for. The cells are 24px, so the icons are also
bigger than the ones they replace, and they are ordinary enlarged cells — which means hover,
tooltips and full-cell click targets come from the same code path as every other cell in the UI
rather than from a special case. The wheel scrolls a whole row per notch, because stepping one item
would slide every icon in the grid sideways.

The firearm cards had to leave the bottom left, because the scanner now takes that column's whole
height. They moved into the room the status panel gave up, which is why they are half again as wide
and gun names stop being trimmed. **While looting they stay under the chest**, where nothing else
wants the space.

### Motion

Everything is off one switch, `animations.enabled`, and **off means settled, never frozen** — every
animated value draws in its final state rather than at frame zero.

**Slot positions can never move.** `Slot.x/y` are final and the menu is built on both sides, so
motion is confined to two places that cannot desync from a hitbox: the whole-canvas transform, and
decoration the mod draws itself. The canvas eases up from 96% on open — and `toLogicalX/Y` read the
*animated* transform, not the settled one, so a cell clicked mid-animation is the cell under the
cursor. Panels reveal their corner tape and titles on a stagger; cells, nav tabs and the selection
ring ease; the status strip's bars slide to their value with a paler trail draining behind them,
which is what makes a hit legible — without it a bar is simply shorter than it was a moment ago and
there is nothing to notice.

**Switching tabs slides.** Each tab is a separate `Screen` and the old one is gone before the new
one exists, so there is nothing left to ask "where did I come from?" — and the Inventory tab does
not even open its screen directly, it asks the server and the reply arrives a round trip later. The
direction is therefore stashed on the click and collected by whichever screen initialises next,
exactly the way the cursor position already was.

**One thing that is not possible:** a real close animation. Minecraft destroys the screen the
instant it closes, so there is nothing left to animate.

### The HUD: the firearm card

Two full custom HUDs were built first — portrait, stat bars, experience bottle, the lot — and a
seven-slot hotbar after them. **None of them shipped.** The vanilla HUD is good, and every attempt
at replacing any part of it was worse than what it replaced. All nine hotbar slots, hearts, armour,
hunger and the experience bar are now left exactly as they are.

The one addition is a card in the bottom right for the two firearms — the part vanilla cannot know
about, since hotbar slots 1 and 2 are firearm-only and nothing on the vanilla bar says so.

```
                                              SEMI      021/999
                                             +-----------------+
                                             | 1 [ gun art  ]  |
                                             +-----------------+
                                             | 2  ~ other gun ~|   <- dim, 14px
                                             +-----------------+
```

The gun in hand is drawn large with its round count and fire mode; the other sits underneath as a
short, wide, low-contrast strip, so you know what the other key hands you without a second
full-size picture costing you view.

**The card never hides.** It stays up whatever is in your hands and merely dims when neither gun
is, because a weapon you cannot see is a weapon you forget you have. Rounds are magazine **plus**
the chambered round, matching what TACZ itself shows — and TACZ's own readout is suppressed, since
it is the thing being replaced and occupies the same corner. Its heat bar, kill counter and interact
prompt are left alone.

**Reserve ammo (`/9999`) only appears for guns using TACZ's dummy ammo**, where the spare count is a
plain number on the stack. With real ammo items the reserve lives in the gun's data pack entry,
which is not in the client's resource manager, so the card omits it rather than guessing.

The gun artwork is read from TACZ's own display file out of the resource manager, since gun packs
are mounted there like any other pack. If a pack names things differently the card falls back to
the conventional path, then to drawing the item itself — which for a TACZ gun is its 2D inventory
icon and still reads as the right weapon.

### The visual pass

The palette was not what made the old screen ugly — the hierarchy was. Every surface carried a
solid `FF463629` edge and 2px corner tape: panels, cards, equipment cells, nav buttons. With that
much emphasis applied everywhere, nothing could be emphasised, and a perfectly good colour scheme
read as clutter.

* Borders are hairlines now, at about 40% alpha. `BORDER_HI` is what a surface *earns* by being
  active, rather than the default state of everything.
* Corner tape is 1px, and is gone entirely from armour cells — on the firearm cells it appears only
  once they hold something or are under the cursor, and thickens to 2px on hover.
* The base surfaces are deepened slightly and the health, armour and food colours pulled back about
  15%. They are instrumentation; at full saturation three bars of primary colour fought each other
  *and* the accent, which is supposed to be the only thing on screen that shouts.

### Also

`build-and-install.bat` and `.sh` were still looking for `tacinv-*.jar`, a name nothing has produced
since the 3.1.0 rename, so both would report "no jar was found" after a successful build. Fixed.

---

**v3.2.0** — the vehicle menu actually works. 3.1.0 did not compile, and would not have worked if it
had; both causes are described under "What went wrong in 3.1.0" below.

Replaces the vanilla player inventory with a full-screen survival UI. Drawn entirely procedurally —
**the mod ships zero GUI textures**, icons included.

**Everything is a display layer.** Quests and vehicles are fed by OP-only commands. The mod awards
nothing, decides nothing, validates no progress — Skript owns the logic, the mod owns the pixels.

---

## What went wrong in 3.1.0, and what fixes it

**It never built.** Cutting the nav bar from five tabs to three deleted `NavTab.isUnlocked()`, but
`NavBar` still called it in two places. That is a hard compile error, so no jar was produced from
that source at all. My structural check missed it twice over — it read three lines of javac context
when the decisive `location:` line is the fifth, and it matched only fully-qualified class names,
which javac prints *only* for classes it could resolve. The check has been rewritten and then
verified by reintroducing that exact bug and confirming it is caught.

**The Summon button was drawn off screen.** `EntityRenderDispatcher.render` pushes a pose and, when a
renderer throws, rethrows **without popping it**. So a vehicle whose renderer failed left the pose
stack one frame deep, and everything drawn after it — the button, the status line — inherited the
entity's own 14× mirrored transform. The button was always there; it was just somewhere off the
monitor. The screenshot showed exactly this: the panels, list and name drew fine, and everything
after the model was gone.

The fix is architectural rather than a patch: **the entire interface is drawn first, and the model
afterwards on a separate `PoseStack` the UI never touches.** A total failure inside another mod's
renderer can now cost the picture and nothing else. The swallowed exception is also logged now, once
per entity id, so a vehicle that will not render can finally be diagnosed — and
`widgets.showVehicleModel = false` turns the 3D preview off entirely while leaving the menu fully
usable.

**Seven more defects** were found by review before release and are fixed: summoning your *last*
consumable deleted its own row and stranded the vehicle with no Store button; the Store button did
not exist when nothing was selected; `/VehicleMenu take` despawned whichever vehicle was out rather
than the one being taken; the retry counter could never reach its limit, so nothing was ever logged;
a returning vehicle could be destroyed by the ownership cap; the button showed "SUMMON" in a
forbidden dimension or zone because the client was never told; and with a zero cooldown two fast
clicks spent two units for one vehicle.

---

## Also in 3.2.0

**The cursor stays put** when you switch screens from the nav bar. Because the Inventory tab asks the
server to open its screen, the position has to be stashed on the click and collected by whichever
screen initialises next, rather than saved and restored around one call.

**Panels are more readable** — `theme.panelOpacity` 160 → 200. The backdrop is still 45, so the world
is still visible around and behind the UI; lower it again in `mlum-client.toml` if you preferred it.

---

## What changed in 3.1.0

| | |
| --- | --- |
| **Renamed** | `tacinv` → `mlum`, and every player's quest board and garage is carried across on first login |
| **Retheme** | Warm and matte instead of blue-steel and backlit. The RPG level bar and XP readout are gone |
| **Removed** | The map system and the team system, entirely. Nav bar 5 → 3 |
| **Chest view** | A separate layout: chest and inventory take the width, stats and ground scanner hidden |
| **Arabic** | Tooltips no longer render backwards |
| **Vehicles** | 3D showcase fixed and now diagnosable, consumable stacks, and summon zones |

---

## 1. The rename, and your existing data

The mod id is now `mlum`. That matters because the id is part of every saved key, so a plain rename
would have orphaned every player's quest board and garage — the data would still be on disk under a
name nothing reads.

`LegacyMigration` copies it across on each player's first login: quest lines, claimed rewards, owned
vehicles, the active vehicle and its cooldown. It runs once, only fills keys that are absent, and
removes the old ones afterwards, so a second login does nothing. Map and team data is dropped rather
than carried, because those systems no longer exist.

**Your config files do not migrate.** Forge names them after the mod id, so you will get fresh
`mlum-server.toml` and `mlum-client.toml` with defaults. Copy your old values over from the
`tacinv-*.toml` files before deleting them.

---

## 2. The theme

The old palette was blue-grey steel with a bright gold accent — a clean, lit, military readout. This
one is the opposite:

* **Surfaces** are oiled canvas and dark cardboard, warm rather than cool, and matte. The top-lit
  gradients that made panels look like polished plastic are gone.
* **Text** is bone white, faded rather than glowing.
* **Corners** get two thick tape marks on opposite corners instead of four thin brackets — a label
  taped down, not a heads-up display.
* **Headers** carry a printed double rule instead of a fading accent gradient.
* **Semantics** are dried blood, ration tin, galvanised steel and field olive.
* **The RPG bits are gone** — no level number, no XP bar.

Everything still recolours from one config value, so `theme.accentColor` is still the single knob.

---

## 3. Chest view

Opening any chest, barrel or ender chest switches the whole screen to a looting layout:

```
                       [Q][I][V]
 +----------------------+  +----------------------+
 |  CHEST          6x9  |  |  INVENTORY      3x9  |
 |  [ ][ ][ ][ ][ ][ ]  |  |  [ ][ ][ ][ ][ ][ ]  |
 |  [ ][ ][ ][ ][ ][ ]  |  +----------------------+
 |  [ ][ ][ ][ ][ ][ ]  |  |  QUICK ACCESS    x7  |
 +----------------------+  +----------------------+
                                [][][][][]   gear
 |[1]     |[2]     |
 +--------+--------+
  Created by BarBwra
```

**Hidden while looting:** the status panel and the ground-item scanner. **Kept:** the hotbar, the
two firearm cells, and worn gear — which lines up in a strip under the quick access rather than
flanking a character portrait, because a chest screen is not the place for one.

The chest grid uses the same 28px cells as the player's own inventory, so both halves read as one
surface rather than a big grid next to a small one.

Hiding the scanner also stops the server scanning the ground for that player at all — the scan is
driven off the slot count, and a looting player has none.

---

## 4. The Arabic tooltip fix

Vanilla's `renderTooltip` runs its text through `Language.getVisualOrder`, which applies Java's bidi
algorithm. The mod's lang files are **already baked into visual order at build time**, so vanilla
reordered them a second time and every Arabic tooltip came out backwards — `المركبات` rendering as
`تابكرملا` — while the exact same string drawn in a panel header was correct.

Tooltips are now drawn by the mod, with `drawString`, the same path as every other piece of text in
this UI. Vanilla's text pipeline is never involved.

Text arriving from outside the mod — item names from other mods — goes through
`ArabicText.autoDisplay`, which shapes raw Arabic and leaves already-baked text alone. It tells them
apart by checking for characters in the base Arabic block: raw text has them, baked text does not.

---

## 5. Vehicles

### Two kinds

| | Persistent | Consumable |
| --- | --- | --- |
| Summoning | free, any number of times | **spends one** |
| Storing | just puts it away | **returns one to stock** |
| Already out | Summon is refused until you Store | same |
| Destroyed | still owned | **gone for good** |
| Stacking | one deed | counted, e.g. 5x AH-6 |

Both are **data in the player's NBT**, never items. A helicopter never takes an inventory slot and
can never be dropped, traded, or lost to a full bag.

```
/VehicleMenu <player> give <entityId> ["<name>"]
/VehicleMenu <player> give consumable <entityId> <count> ["<name>"]
/VehicleMenu <player> take <entityId>
/VehicleMenu <player> clearall
/VehicleMenu <player> lock <seconds>      -- impose a PvP lock from Skript
/VehicleMenu <player> unlock
```

```
/VehicleMenu Steve give mod:modded_vehicle
/VehicleMenu Steve give consumable mod:ah6 5 "AH-6"
```

The button says **SUMMON** with nothing out and **STORE** once something is. Store is the only thing
that returns a consumable — walk away from it, or let it be destroyed, and it is spent.

### Where summoning is allowed

```toml
[vehicle]
    allowedDimensions = ["minecraft:overworld"]
    blacklistZones = ["100,4,200,100"]
```

Each zone is `x,y,z,distance` — no summoning within that radius of that point. Measured from the
**player**, not the spawn spot, so standing just outside a safe zone and aiming a vehicle into it
does not work. A malformed line is logged and skipped rather than throwing, so one typo cannot stop
every player on the server from using a vehicle.

### The 3D showcase

The selected vehicle is instantiated client-side and drawn with the game's own renderer, so it looks
exactly like what will appear in the world.

Two fixes this version. First, the entity is now **seeded before rendering** — position, both
rotation histories, and a non-zero age — because a showcase entity has never been ticked and many
modded renderers read interpolation fields on the very first frame.

Second, and more importantly: the previous version **swallowed the exception silently**. A vehicle
that would not render produced a polite message on screen and nothing at all in the log, which made
it impossible to diagnose. It is now logged once per entity id, with the stack trace. A few attempts
are allowed before an id is given up on, since some renderers throw on the first frame while a model
is still loading.

**If a vehicle still shows "cannot display the model", send me `latest.log`** — the reason will be in
there now.

---

## 6. Everything else, unchanged

Full-screen 16:9 canvas respecting GUI Scale. Enlarged cells — 28px inventory and chest, 36px worn
gear, 48px firearms — repainted over vanilla's 16px versions, with only the outer ring of each frame
routed by hand so drag-splitting and double-click-collect keep working. Two firearm cells enforced
in three places. Ground item scanner. Skript-driven quest board with reward items and a claim button.
See-through panels.

The firearm label now reads **أسلحة نارية فقط**.

---

## Build

Needs a **JDK** (17 or newer; the wrapper is Gradle 8.5, which handles Java 21).

```
build-and-install.bat        # Windows, double-click
./gradlew build              # any OS
```

---

## Commands

| Command | Who | What |
| --- | --- | --- |
| `/PlayerQuest` | OP / console | Quest lines, progress, descriptions, reward items |
| `/VehicleMenu` | OP / console | Grant and revoke vehicles, impose PvP locks |

Both have a lowercase alias, because command blocks and Skript are case sensitive about literals.

---

## Config

`config/mlum-server.toml` (per world, synced to clients):

| Key | Default | |
| --- | --- | --- |
| `takeover.replaceInventory` | `true` | take over the inventory key |
| `takeover.replaceInCreative` | `false` | set true if you test in creative |
| `takeover.takeoverChests` / `takeoverBarrels` / `takeoverEnderChest` | `true` | |
| `vicinity.radius` | `4.0` | ground scan radius in blocks |
| `weapons.allowedItems` | `["tacz:modern_kinetic_gun"]` | ids the firearm cells accept |
| `weapons.enforceGunSlots` | `true` | the two-way hotbar sweep |
| `quest.claimCommand` | `mlum_claim %player% %line%` | blank disables the claim button |
| `vehicle.allowedDimensions` | `["minecraft:overworld"]` | where summoning works at all |
| `vehicle.blacklistZones` | `[]` | `x,y,z,distance` entries |
| `vehicle.combatLockSeconds` | `15` | the PvP lock; 0 disables |
| `vehicle.summonCooldownSeconds` | `30` | |
| `vehicle.maxOwned` | `24` | a vehicle coming back from Store ignores this cap |

`config/mlum-client.toml` (cosmetic, never synced):

| Key | Default | |
| --- | --- | --- |
| `theme.accentColor` | `E8A33D` | RRGGBB; the whole UI recolours from this |
| `theme.panelOpacity` | `200` | 0–255 |
| `theme.backdropOpacity` | `45` | darkening behind the UI |
| `theme.fillFraction` | `1.0` | 1.0 = edge to edge on a 16:9 display |
| `widgets.showPlayerModel` / `showWatermark` | `true` | |
| `widgets.showVehicleModel` | `true` | turn off if a vehicle mod's renderer misbehaves |

| `hud.firearmCard` | `true` | both guns bottom right; also hides TACZ's own ammo readout |
| `animations.enabled` | `true` | off draws every animated value in its settled state |
| `animations.speed` | `1.0` | 0.25–3.0 |

---

## Structure

```
com.barbwra.mlum                         49 source files
├── MlumInventory.java         entrypoint: config, menus, network
├── MlumConfig.java            SERVER (synced gameplay) + CLIENT (cosmetic)
├── menu/                      ★ MlumLayout: every pixel of both layouts and both screens
│   └── slot/                  Armor, Offhand, Vicinity, Gun, Quick
├── quest/                     command, per-player board, reward parsing
├── vehicle/                   garage, entry kinds, combat lock, summon rules, command
├── util/                      ArabicText (shaper + UAX #9), LegacyMigration
├── compat/TaczCompat.java     firearm detection with no TACZ dependency
├── client/                    the three screens, tooltips, easing, cached server state
│   └── hud/                   the firearm card and its TACZ artwork lookup
├── network/                   SimpleChannel + 4 packets
└── events/                    key interception, chest takeover, hotbar sweep, commands, PvP tags
```

**Why the layout is one file.** `Slot.x/y` are final and the menu is built on **both** sides, so the
layout must be a pure function of the container row count — not the window size, not client config.
`MlumLayout` is that function, and the chest view is the same function answering differently.

---

## Notes

* **Both sides need the mod** — it registers a `MenuType` and opens screens from the server.
* Only vanilla chests, barrels and ender chests are taken over. Every modded container keeps its own screen.
* The Arabic lang files are **pre-shaped**: baked into Presentation Forms-B and pre-reversed.
  Regenerate with `python genlang.py` after `pip install arabic-reshaper python-bidi`. Never
  interpolate a number into one of those strings — draw label and value as separate elements.
* Nothing in this mod ever destroys a player's item. When the hotbar sweep finds a misplaced stack
