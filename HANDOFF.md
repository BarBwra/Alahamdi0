# MlumInventory — handoff

Everything needed to work on this mod without having seen it before. **Read it all before changing
anything**; several sections describe traps that have already cost real debugging time, and a few
describe bugs that were shipped and then found the hard way.

Last updated at **mlum 3.17.0**, network protocol **19**.

---

## 1. What this is

`MlumInventory` (mod id `mlum`) is a **Forge 1.20.1** mod for one private Arabic-language
zombie-apocalypse survival server. It is not general purpose — it is that server's systems bundled
together:

- A **custom full-screen inventory** that replaces the vanilla one entirely
- A **grid bag** with per-item footprints, plus backpack rows
- A **wallet**, a **skills** tree, a **quest board**, a **vehicle garage**, a **warehouse/market**,
  **safe zones**, a **faction system** (المنظمة), **ranks**, and an in-game **firearm HUD**

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 (the user's client runs 47.4.23) |
| Mappings | official (mojmap) |
| Java | 17 target, built with JDK 21 |
| Working dir | `C:\Users\BarBwra\Desktop\!mods\mlum-inventory 3.3.0` (folder name is stale, the mod is 3.8.0) |
| Installed to | `C:\Users\BarBwra\curseforge\minecraft\Instances\Mlife\mods\` |

**There is no git in this project.** Nothing is recoverable once overwritten. Do not bulk-rewrite
files without an explicit instruction, and make a copy first when you do.

### Sibling projects in `!mods`

| Folder | Mod id | Notes |
|---|---|---|
| `mlum-inventory 3.3.0` | `mlum` | this mod |
| `mlum-base 1.0.0` | `mlum_base` | per-player base building. **A crash was fixed here — see §9.** |
| `mlum-shop 1.0.0` | `mshop` | shop; `mlum` reads sell prices from it via `ShopCompat` |
| `mlum-map 1.0.0` | `mmap` | map |
| `mlife-warehouse 1.0.0` | — | older warehouse; the live one is bundled inside `mlum` |
| `mlumarmor` | `mlumarmor` | **built in a session I was not part of — undocumented here** |
| `Armor Model` | — | model work, same caveat |
| (installed only) | `mlum_scarab` | **no source folder found in `!mods` — undocumented here** |

### Build and install

```bash
cd "C:\Users\BarBwra\Desktop\!mods\mlum-inventory 3.3.0"
./gradlew build --offline
```

Produces `build/libs/mlum-<version>.jar`. Then delete `mlum-*.jar` from the instance's `mods` folder
and copy the new one in. `build-and-install.bat` does both but points at a `claude` instance by
default — the real one is `Mlife`.

**A compile error means no jar is produced and the old one stays installed.** Always check the jar's
timestamp and size after building, never just the "BUILD SUCCESSFUL" line. This has caused "I
installed your fix and nothing changed" more than once.

Verify no foreign classes leaked in after touching dependencies — must print `0`:

```bash
unzip -l build/libs/mlum-3.8.0.jar | grep -cE "com/tacz|atsuishio"
```

### The version / protocol rule

`ModNetwork.PROTOCOL` is a string Forge refuses connections over when it differs. **Bump it whenever
any packet changes shape, and bump `mod_version` in `gradle.properties` with it.** A stale client
otherwise decodes one packet as another and draws confident nonsense. The history is in the javadoc
on that field; it is at **10** now.

---

## 2. Optional-dependency mods

All are the user's pack, none are bundled.

| Mod | Used for | How referenced |
|---|---|---|
| **TACZ** 1.1.8-hotfix | guns, attachments, ammo | `compileOnly` jar in `libs/`; `compat/TaczAttachments`, `compat/TaczCompat`, and **one mixin** |
| **Superb Warfare** 0.8.9.1 | vehicles | **no dependency at all** — class-name walk + reflection in `compat/SbwCompat` |
| `survivorsarsenal` | backpack items | config item-id list |
| `survival_instinct` | `survival_instinct:money`, the banknote item | item id string |
| `curios` | installed, **not yet integrated** — see §12 |

SBW is Kotlin and ships its own runtime; putting it on the compile path would tie this build to its
version. Everything it needs is a superclass-name check plus four reflected methods.

---

## 3. The bag — the heart of the mod

### 3.1 Two sections, two different owners

| Section | Rows | Where the items actually live |
|---|---|---|
| `BASE` | 3 | **vanilla inventory slots 9..35.** The bag stores only *positions* |
| `PACK` | 0..5 | **NBT inside the worn backpack `ItemStack`** |

This asymmetry is the single most important thing about the bag. A base cell *is* a vanilla slot, so
`BagEntry.stack()` for a base entry is the **live object Minecraft holds** — `split()` and `grow()`
on it edit the real inventory. A pack entry is a deserialised copy that must be written back with
`BackpackAccess.writeGrid`.

Storing base stacks in the bag instead would create 27 slots invisible to hoppers, death handlers and
every other mod — a duplication bug waiting to happen. Storing pack rows on the player would mean the
backpack's contents no longer travel with the backpack.

`BagService.takeFrom` / `putAt` are the **only** two methods that know which is which. Everything
above them moves items without caring.

### 3.2 The bug that broke everything: `BagEntry.source`

`source` is the inventory slot a base entry *is* (or `OWNED_BY_PACK = -1`). It was **not written to
the packet**, so every entry arrived on the client claiming to be pack-owned. Consequences, all of
which read as separate bugs:

- the drop position was never applied — items landed in the first free cell
- `hoveredSlot` stayed null, so **Q dropped nothing**
- shift-click and right-click on base items fell through silently
- the pending-placement handshake never matched

One missing varint. If something in the bag behaves as though the client cannot tell the two sections
apart, check `BagEntry.write/read` first.

### 3.3 Moves: one rule for every landing

`BagService.move(player, from, fromA, fromB, to, toA, toB, amount)` reads what is under the
destination footprint and decides:

| Under the footprint | What happens |
|---|---|
| nothing | placement (reposition if same section and whole stack, otherwise take + put) |
| **one** stack of the same item | **merge** |
| **one** stack of something else | **swap** (whole stacks only) |
| two or more | refused |

Before this, anything not completely empty simply failed — which is why two stacks of the same wood
could never be joined however they were dragged.

`amount` is a **ceiling, not an instruction**: the server clamps it to what is really there. `0` means
all. That one parameter is what makes right-click-place-one and split-half work.

### 3.4 Vanilla-feel gestures (client, `BagScreen`)

| Gesture | Behaviour |
|---|---|
| left-click item | pick up the whole stack (`heldAll = true`) |
| right-click item | pick up half (`heldAll = false`, `heldCount` fixed) |
| left-click cell while holding | put it all down |
| right-click cell while holding | put **one** down |
| right-click + drag | paint one into each cell crossed (`painted` set stops doubles) |
| double-click source cell | **gather** every other stack of that item in the same section |
| shift-click | quick move |
| `Q` / `Ctrl+Q` | drop one / drop the stack, on the ground |
| click the dark outside | drop what is held / carried |
| `Esc` | let go |

**`heldAll` is a flag, not a number**, on purpose: "all of it" has to keep meaning all of it while the
stack is changing under the cursor — placing one shrinks it, a double-click grows it. A count frozen
at pick-up would go stale on the first of either.

Gather is **within one section only**. The pack's rows travel with the backpack; a tidy-up gesture
should not quietly change what a player keeps when they take it off.

### 3.5 Two kinds of "holding"

- **grid-held** (`held` in `BagScreen`): the item stays in its cell on the server and is drawn under
  the cursor. This is what lets a 7×2 rifle show its real footprint.
- **vanilla cursor** (`menu.getCarried()`): anything picked up from a real slot — quick access, gear,
  a chest cell.

Dropping a cursor stack onto the grid goes through `placeCarried` → a free vanilla slot →
`pendingSource` → `applyPendingPlacement` sends the real move once the entry appears. Two round trips,
but it is the only way to get a cursor stack into an exact cell.

### 3.6 Hover feedback

`Slots.OPEN` (state 5) is the amber square on an **empty** cell under the cursor. Applied to the bag
grid, quick access, all six gear sockets and the empty firearm cards. `Slots.slot` originally only
reacted to hover when the cell held something.

---

## 4. The wallet — an account, not an item

**This changed and it is a behaviour break.** The wallet used to be a *count of
`survival_instinct:money` items* carried in the bag.

Now:

- `WalletStore` — one `long` in the player's persisted NBT
- the banknote is an ordinary item that takes a cell like any other
- `WalletService.give` **always succeeds** — no "no room", nothing dropped on the floor
- `EconomyService.pay` can no longer half-fail, and `take` no longer rummages through the inventory

```
/mlum_inventory money set|add|remove|get <players> <amount>
/mlum_inventory money deposit  <players> <amount>   # notes  -> balance
/mlum_inventory money withdraw <players> <amount>   # balance -> notes
```

`deposit`/`withdraw` are the deliberate bridge between the two. They are **not** automatic — money
turning itself into an account balance on pickup would be the two things joined again.

**Migration note:** existing players' balances started at 0 when this shipped. Their banknotes were
left untouched. `deposit` is how they are converted.

Synced by `S2CWallet` on login, respawn, dimension change and every change. The client has no other
way to know it — `UiState.tickWallet` reads `ClientWallet`, not an item count.

---

## 5. Skills — they used to do nothing at all

### 5.1 What was wrong

The tab sold perks and then ran a console command, `mlum_skill %player% %skill% %level%`, leaving the
effect to a server script. **That command was never written**, and the call site used
`withSuppressedOutput()` — so buying a skill took the money, marked the card complete, and changed
nothing, silently. The default `buyCommand` is now blank and the effects are in code.

### 5.2 The current ladder

Config line format (the 11th field is optional):

```
id|name|description|icon|level1|level2|level3|price1|price2|price3[|soon]
```

| id | name | what it does | where |
|---|---|---|---|
| `attachments` | تعبئة أسرع | reloads 15 / 30 / 45% faster | mixins, §5.4 |
| `quiet_hands` | إيد خفيفة | the fast search's noise chance 50% → 40 / 30 / 20% | `SkillEffects.noiseChance` |
| `scout` | الباحث | container markers through walls, 8/12/16 blocks; level 3 dims the empty ones | `LootMarkers`, `ScoutInfo` |
| `butcher` | الجزار | zombies drop more meat | `LivingDropsEvent` |
| `blade_master` | السلاح اليدوي | melee kills drop more of **everything** | `LivingDropsEvent` |
| `medic` | المسعف | everything that heals you heals more | `LivingHealEvent` |
| `soon_1..3` | قادم قريباً | drawn, not buyable | — |

The id `blade_master` was **kept** when the skill was renamed, so anyone who had bought it keeps it.

`maxedSkills` (default 3) caps how many may reach level 3. **Dropping a skill costs
`refundCost` (2000) and returns nothing** — it buys the right to change your mind and frees a maxed
slot. A refund would make the cap meaningless.

### 5.3 Fractions are rolled, not floored

A 15% bonus on a single-item drop is 0.15 of an item. Flooring it would make every one-item drop in
the game — which is most of them — ignore the skill entirely. `SkillEffects.scale` rolls the
remainder, so it pays out about right over a session rather than exactly right per kill.

### 5.4 The TACZ mixins — the reload skill

The id `attachments` was **kept** so anyone who bought the old تعشيق أكثر skill keeps their level; it
is now تعبئة أسرع. (The old skill — extra attachment slots — was dropped at the user's request, along
with `TaczAttachmentMixin`, `GunUpgrade` and `SkillGuns`.)

TACZ does not count a reload down. It stores `reloadTimestamp` when the reload starts and every gun
script asks `ModernKineticGunScriptAPI.getReloadTime()` (`now - reloadTimestamp`) when to feed the
rounds and when to finish — the default tick and every Lua script (e.g. `xmag_reload_logic.lua`).

- `mixin/TaczReloadMixin` (common): `@Inject RETURN` on `getReloadTime`, multiplies the answer by
  `ReloadSkill.factor(getShooter())` — ×1.15 / ×1.30 / ×1.45. Zero (no reload) stays zero.
- `mixin/TaczReloadAnimMixin` (client): `@ModifyVariable HEAD` on
  `ObjectAnimationRunner.updateProgress(long)`, scaling the frame step for animations whose name
  starts with `reload`, so the hands keep up with the rounds.
- `ReloadSkill.clientLevel` is set in `ClientSetup` so the common class never names a client type.

All `remap = false`, `defaultRequire 0` (a TACZ update that moves these methods turns the skill off
instead of crashing the server). `MlumMixinPlugin` switches the config off when TACZ is absent.

**Not tested in game.** Check the log for mixin warnings after the first launch.

### 5.5 The medic window

Scoped to 200 ticks after the player finishes or right-clicks an item, rather than to a list of item
ids — the server runs several mods that heal, each its own way, and a list would be wrong the day a
new one is added. Both `LivingEntityUseItemEvent.Finish` **and**
`PlayerInteractEvent.RightClickItem` open the window: `Finish` only fires for items with a use
duration, and most medical items on this server heal on the click itself.

---

## 6. Ranks and the store

- `rank/Rank` — id, name, colour, **price as free text**, blurb, perks
- `rank/RankService` — config ladder (lowest first), per-player rank in persisted NBT
- `S2CRanks` → `ClientRanks` — ladder + money packs + which rank this player holds, **in one packet**
  (the bar draws the held rank *from* the ladder; a frame where they disagree draws a rank with no
  entry behind it)
- `client/ui/view/StoreView` — the dialog
- the top bar's pill (`Chrome.rankPill`) **replaced the safe-zone pill**

**Nothing here charges anyone.** Ranks are sold outside the game; the price is a string so a server
can write `50 ريال` or leave it blank. The store is a catalogue, and ranks change hands only through:

```
/mlum_inventory rank set <players> <rank>
/mlum_inventory rank get <player>
```

The rank badge is **generated** (initials in the rank's colour, framed, with a radial bloom) rather
than a texture, so a rank exists the moment it is written in the config instead of waiting on
artwork.

Store state lives in `UiState` (`storeOpen` / `storeTab` / `storeDetail`), not on a screen, because
the top bar is drawn on all six tabs and switching tabs must not close it. Both `BagScreen` and
`TabsScreen` call `UiState.storeClick(hit)` before their own routing and `UiState.storeModal(hover)`
from `modal()`.

Config section `[ranks]`: `id|name|colour|price|blurb|perk;perk;perk`.

---

## 7. Vehicles

### 7.1 The lock (`VehicleLock`, `VehicleAccess`)

Three modes, stored in the **entity's** Forge persistent data so they survive relog and chunk unload
with no side table:

| Mode | Who rides |
|---|---|
| `LOCKED` (default on summon) | the owner |
| `FACTION` | the owner + their organisation |
| `OPEN` | anybody |

`L` cycles it, **owner only** — a faction-mode vehicle lets members ride, not decide who else can.
Refusal plays `CHEST_LOCKED` and is throttled to once per 40 ticks so a held use-key is not a machine
gun. Changing the mode ejects anyone no longer allowed.

**Three enforcement gates, and all three are needed:**

1. `PlayerInteractEvent.EntityInteract` — fires **before** the entity's own `interact`
2. `EntityMountEvent` — ordinary mounting
3. a per-tick sweep every 10 ticks — **this is the one that actually holds**, because a vehicle mod
   moving a player between its own seats does it over its own network channel and fires no Forge
   event at all

#### The bug I shipped here, and the lesson

SBW has its own `Locked` boolean. I mirrored our mode onto it so its padlock icon would not
contradict us. That **broke the feature outright**: its interact handler is

```java
if (getLocked()) { tell("vehicle.locked"); return FAIL; }   // no owner check whatsoever
```

so a freshly summoned (locked) vehicle shut its own owner out before any of this mod's code ran. A
boolean cannot express "mine may ride, strangers may not" — which is the entire reason `VehicleLock`
exists. **SBW's flag is now forced off, always**, and `VehicleLock.clearForeignLock` is called on the
way into every interaction so vehicles already stuck from that build repair themselves on first use.

### 7.2 Energy

`SbwCompat.refuel` reaches `getEnergy` / `setEnergy` / `getMaxEnergy` / `hasEnergyStorage` by
reflection — all public on `VehicleEntity`, so no NBT guessing and no compile dependency. Full on
summon, topped up every 10 ticks while anyone is riding. `infiniteVehicleEnergy` config, default on.
A vehicle with no storage answers `hasEnergyStorage = false` and is skipped rather than special-cased.

### 7.3 Destruction

`activeId(player) != null` meant "did we write down a UUID", not "does the vehicle exist". So a plane
that blew up left the record forever: the garage said a vehicle was out, Store had nothing to store,
and **no further vehicle could ever be summoned**. `VehicleDestruction` hooks
`EntityLeaveLevelEvent` and acts **only** on `RemovalReason.KILLED` / `DISCARDED` — the other reasons
mean the entity is alive and merely unloaded, and acting on those would orphan a parked vehicle.

Spawn search starts **3 blocks** out (was 2) and walks to 9.

Remove vehicles from a player's list with `/mlum vehicle take` or `/mlum vehicle clearall`.

### 7.4 3D models in the garage

`client/ui/mc/EntityPreview` builds one entity per type, keeps it, never adds it to the world, and
renders it with `EntityRenderDispatcher`. **`InventoryScreen.renderEntityInInventory` cannot be
used** — it takes a `LivingEntity`, and an SBW vehicle is a plain `Entity`. A type that throws is
remembered as broken and falls back to the flat picture.

Reached through `Canvas.entity(...)` → `McCanvas.entity`, which flushes and clips like `player()`
does, because the entity renderer writes through its own buffer source and depth.

---

## 8. The menu guard (حماية المأفكي)

While one of this mod's menus is open, hostile mobs may not **pick** that player — unless something
is already within `menuGuardRadius` (10) when it tries. You cannot open the bag to escape a fight,
because the thing you are escaping is what switches the guard off.

The bag is a real container the server can see on `containerMenu`. The other five tabs are plain
screens that never touch the server, so the client says so with `C2SMenuOpen` about twice a second and
the claim **goes stale on its own** after 100 ticks — a crash or a lost connection cannot leave anyone
permanently unattackable.

`LivingChangeTargetEvent` only fires when a target *changes*, so a mob that locked on before the menu
opened would come forever. A second sweep clears held targets.

**A modified client can lie about this.** The exploit is small by construction; `menuGuard` in the
config turns it off.

---

## 9. The `mlum_base` crash — fixed, and worth remembering

Every death crashed the server, exactly one second later:

```
[18:54:07] BarBwra was slain by Zombie
[18:54:08] ERROR: Player BarBwra has no mlum_base capability attached
```

Twenty ticks after a player dies, Minecraft calls `Entity#remove` and Forge invalidates every
capability on them — **but the player stays in `PlayerList` until they click respawn**. So for the
length of the death screen there is a live entry in the player list with no capability behind it.
`ModCapabilities.of()` threw `IllegalStateException`, and it was called from the once-a-second server
tick. An exception out of the tick loop is a server crash.

Fix: `ModCapabilities.find()` returns null; `of()` falls back to a detached instance and logs; every
loop that walks the player list skips removed players. **Anything iterating `getPlayerList()` must
assume a player there may have no capabilities.**

---

## 10. Arabic text — the biggest trap in this codebase

The lang files (`assets/mlum/lang/*.json`) are **pre-baked into Unicode Presentation Forms-B and
pre-reversed**. They are *not* logical-order Arabic.

- `Language.getVisualOrder` / `Component#getVisualOrderText` on them **double-reverses** → garbage
- `util/ArabicText.autoDisplay(String)` shapes only if `isLogical()` says it needs it
- **Arabic written literally in Java source is logical order** and must go through `autoDisplay()`
  before being drawn by vanilla or sent to chat
- The mod's own UI (`TextEngine`, `Css.txt`) handles logical Arabic itself — literals in view code are
  fine as-is
- `Css.px()` uses the **pixel font, which has no Arabic glyphs**. Key caps and badges must be Latin
- Key-binding names in the vanilla Controls screen are English for this reason

To add a lang entry, do not hand-type the shaped form: compile `ArabicText` standalone (it has zero
imports) and run it on the logical string.

---

## 11. Config

**One config id, two files**, both named `mlum`:

| Type | Path | Synced |
|---|---|---|
| SERVER | `saves/<world>/serverconfig/mlum-server.toml` (dedicated: `world/serverconfig/`) | yes |
| CLIENT | `config/mlum-client.toml` | never |

Plus `config/mlum_inventory.toml` — the bag's own NightConfig file (backpacks, item sizes, gun sizes,
rarity), reloadable with `/mlum_inventory reload`.

### ⚠ The trap that will waste your time

**Forge keeps existing values for existing worlds. A changed default only affects fresh worlds.**

So after changing `skills`, `ranks`, or any list default, the user's live server **will not see it**
until they delete that section from `world/serverconfig/mlum-server.toml`. Say so every time.

**Since 3.10.0 there is a way round it: `ConfigMigration`.** The server file carries
`configVersion`; each step there runs once on load, replaces a value only if it still holds the *old
default*, and bumps the number. When a release changes a default the live server should get, add a
step there (and bump `CURRENT`) instead of asking the user to edit the file. The
local test world `New World (4)` was edited by hand for this reason (backup at
`mlum-server.toml.bak-before-3.7.0`).

Sections added recently: `[menu_guard]`, `[ranks]`, `infiniteVehicleEnergy`, `skills.refundCost`,
client `lockHudX` / `lockHudY`.

---

## 11b. Added in 3.9.0 — field HUD, timed search, downed system

### Field HUD (`client/hud/field/`)
`FieldHud` replaces vanilla health/armor/food/hotbar/item-name when `fieldHud = true` (client config):
wrist device bottom-left (XP edge, ECG, `ArmorEmblem` around the health %, food bar), belt carousel
bottom-centre, weapon slab bottom-right (TACZ art, ammo, magazine ticks via
`TaczAttachments.magazineSize`), compass at the top. Accent colour `fieldHudAccent`. `ZoneToast` and
`LevelUpToast` render from here now, because cancelling HOTBAR also kills its Post event. The old
firearm card only draws when the field HUD is off. `HudPen` is a thin wrapper over `McCanvas`.

### Loot markers and the timed search (`loot/`, `client/loot/`)
- Markers are drawn **in screen space** by projecting with the matrices captured in
  `RenderLevelStageEvent` (`WorldProjector`). World-space lines are dropped by shader packs — that is
  most likely why the old scout outlines were never seen.
- One right-click on a container starts a search (`LootSearch`, server) — no holding: 1.5 s, or
  0.75 s while sneaking with a 50% chance (less with `quiet_hands`) of a noise that pauses 0.5 s and
  pulls nearby monsters. Moving, taking damage or a left click cancels. On completion the normal
  open path runs (`ServerEvents.tryTakeover` or `BlockState.use`). Config `[loot_search]`.
- While searching or reviving, `RummageHands` cancels the normal first-person hands (TACZ's gun
  included) and draws both bare arms in vanilla's two-handed-map pose, digging. `SearchSpinner` is
  the shared eight-dot spinner; `Shapes` draws anti-aliased rings out of row runs.

### Downed and revive (`downed/`, `client/downed/`)
- Lethal damage downs a player instead (`DownedService`, HIGH `LivingDeathEvent`): 360 s bleed-out,
  smaller hitbox via `EntityEvent.Size`, drawn in the sleeping pose for others, camera locked to the
  sky. Mobs drop and refuse targets on downed players. Players can finish them (config).
- F (`key.mlum.interact`) on a body: wheel picks loot / revive (drag was removed in 3.10). Revive = hold 10 s; oxygen item
  in hand = hold 5 s and consumed; defib item = instant, costs `defibCost` charge (`ChargeTag` NBT).
  Defib and oxygen are **placeholder items** from config (`blaze_rod`, `phantom_membrane`) until the
  real ones are made.
- While down: the inventory key (E) = distress to online faction members (cooldown) - borrowed
  rather than a new binding on E, which would show red in the controls screen; hold F 3 s = give up, which paints
  the timer ring red as it fills. The ring is a real circle with M:SS inside.
- Dying from the ground (bled out, gave up, finished off) leaves everything in one of the configured
  non-VIP backpacks on the ground, the smallest that fits, more than one if needed (`DeathBag`).
- After a revive: slowness + weakness, and a shorter timer if downed again within 120 s.
- Loot view (`DownedLoot`): 54-slot container over the body's inventory, armour, offhand and the
  Curios back slot. The screen gets the body's entity id after the vault bytes
  (`ServerEvents.openBodyScreen`, `MlumMenu.bodyId`) and draws them standing with their gear in a
  small figure panel (`InvView.bodyPanel`, cells 36..41) over their 36 inventory cells.
- Test: `/mlum downed dummy|self|revive <player>|charge|list` (op 2). The dummy is a `DownedDummy`
  entity that can be looted/revived/dragged.

### Also in 3.10.0
- Field HUD: the belt is static (nine even cells, no carousel), the wrist shows the player's face
  (`HudPen.face`, skin UV 8..16 + hat), and `fieldHudScale` (client, default 1.15) zooms each panel
  from its own corner (`HudPen.zoom`). The belt moves above the panels on screens too narrow for it.
- Loot marker mouse redrawn: 15×22 cells sized from screen height, drop shadow, green right button
  that clicks every 1.4 s while focused.
- Gun details in the bag show the round it fires and the magazine size (`TaczAttachments.ammoOf`).
- Quick access row runs left to right (3 on the left).
- Ghillie suits (`camo/Ghillie`, `GhillieClient`), reworked in 3.10.1: a full set, crouched and
  still (within 1 block) for `hideSeconds` (5), sets the server-side invisible flag (hides body,
  shadow, name); every client - the wearer's own third person included - then skips the whole
  player render, so armour, held items and the Curios backpack go too. The bag screen's portraits
  set `DownedClientEvents.portrait` to stay visible. `requireCover` (off) brings back the
  leaves/snow rule. A notice above the wrist device shows how to hide, the count, and "hidden".

### 3.10.1
- Rummage hands: always vanilla's *raised* map pose plus a lift; the lowered pose vanilla uses
  when looking straight ahead put them off screen, which is why searching showed no hands.
- Search shows a Shift keycap hint under the spinner.
- Downed players are held where they fell (server teleports back past 0.3 blocks; the client also
  zeroes its own motion), the selected slot is moved off the two gun slots, and TACZ shoot/reload
  events are cancelled while down (`DownedTacz`, registered only with TACZ present).

### 3.11.0 — staff ranks and the admin panel (`admin/`, `client/admin/`)
- **Only an OP can ever be in creative** (`CreativeLock`): the switch is cancelled for anyone else,
  and every 2 s and at login a non-OP in creative is put back in survival. No rank can grant it.
- **Ranks** (`StaffData`, saved as `mlum_staff`): OP-made, any name and colour, any number. A rank
  holds a set of permission *nodes*; there is no fixed list. `Perms.ALL` is just the labelled nodes
  the panel offers as toggles - `panel`, `players.teleport|bring|inventory|inventory.edit|spectate`,
  `vanish`, `restore`, `punish.warn|mute|jail|kick|ban|history`, `tickets`, `alerts`, `restart`,
  `schedule`. `Perms.covers` handles prefixes (`players` covers `players.*`) and `*`.
- **Any command on the server can be granted**: `CommandGate` wraps every brigadier node's
  requirement at `RegisterCommandsEvent` LOWEST by reflection, so a node passes if it passed before
  *or* the player's rank holds `cmd.<path>` (e.g. `cmd.tp`, `cmd.time.set`, `cmd.give`, or `cmd.*`).
  This covers other mods' commands too. `Staff.refresh` resends the command tree after any change.
- Panel: K (`key.mlum.admin`). Tabs players / ranks (OP only) / punishments / tickets / alerts /
  schedule. Everything goes over `C2SAdmin(action, tag)` / `S2CAdmin(kind, tag)`; each action
  re-checks its node on the server (`AdminActions`).
- Tools: teleport/bring, open a player's inventory (look, or edit with `.edit`; reuses the downed
  loot view in `inspect` mode), spectate, vanish (`Vanish`: invisible, unlisted from Tab for those
  without `vanish`, mobs ignore), restore one of the last 10 deaths (`DeathArchive`, `mlum_deaths`),
  warn / mute / jail / kick / ban with history (`Punish`, `mlum_punish`), tickets (`/mlum ticket`,
  `mlum_tickets`), money/item-value alerts (`EconomyWatch`, thresholds in server config `[admin]`),
  scheduled restart (`Restart`, `restartTimes` HH:mm list; **the server must be started by a loop
  script** for it to come back), scheduled console commands (`Schedule`, `mlum_schedule`).
- Commands: `/mlum staff ranks|rank create/delete/color/add/remove|assign|unassign|check`.

### 3.11.0 — themed screens (removed in 3.16.0)
The redrawn title, ESC, loading and crafting screens (`ScreenSwap` and the `Mlife*Screen`
classes) and their `[screens]` config were deleted at the user's request, as were the news and
side panels before them - do not bring them back. `ScreenKit` stays: the showroom uses it.

### 3.12.0 — feel (`client/feel/`, client config `[feel]`)
- **Shoulder camera** (`ShoulderCamera`): in F5-back the camera is moved to a shoulder inside
  `ViewportEvent.ComputeCameraAngles` via `Camera.setPosition` (SRG `m_90581_`, reflection; disables
  itself if that fails), wall-clipped from eight corners. X swaps sides. An aim mark is drawn where
  the eye ray lands (where TACZ bullets go): a ring with a gun, a dot otherwise, red on an entity.
- Flat dropped items, an inspect key and swinging doors were built in 3.12.0 and removed again
  in 3.12.1 at the user's request - do not bring them back.
- **UI sounds**: `ui.hover/click/open/close` (synthesised oggs) from `UiHost` and `ScreenKit`.
- **Health feel** (`HealthFeel`): under 35% health a red vignette (vanilla texture, darkening blend),
  heartbeat (`feel.heartbeat`) and world sounds muffled up to 60% (`PlaySoundEvent` wrapper).
- Admin panel key moved O→K, off TACZ's interact key.

### 3.12.2 — the faction vault looks like a vault (`client/ui/view/VaultView.java`)
A vault page no longer uses the chest panel: it takes the whole left column (no details panel) as
a steel door - brass trim, rivets, hinges, the faction's name, a combination dial, numbered page
drawers (`vpg:N`, jump straight to a page) and a recessed interior with bolts either side. The
cells are still the `box:` slot nodes, so interaction is unchanged. A fresh opening runs the
unlock (dial spins, bolts draw back, two inner leaves slide apart, ~1.25 s, `ui.vault_open`); a
page turn - the menu reopening within 1.5 s of closing - only spins the dial (`ui.vault_page`).

### 3.13.0 — ghillie timer and shimmer, the vehicle dealership
- Ghillie: the notice above the wrist is now a real countdown (ring + seconds) and draws in any game
  mode, and on its own (`FieldHud.ghillieOnly`) with the field HUD off. `Ghillie.DRIFT` is 0.15 -
  any step or letting go of Shift restarts it. Other clients draw a hidden wearer as a faint
  rippling translucent body (`GhillieClient.shimmer`, `Ripple` vertex wrapper) within 9 blocks,
  stronger when looked at directly.
- Dealership (`dealer/`, `client/dealer/`): opened only by `/mlum dealer open <players>` (level 2;
  a command block at the showroom). That starts a 30-minute server session; buying needs one.
  Price comes out of the bag balance (`WalletService.take`, refunded if `VehicleGarage.give`
  refuses). Requirement: vanilla XP level. Limited = `VehicleEntry.consumable` with the listing's
  count; otherwise a deed. Stock is `mlum_dealer` SavedData, edited in game by anyone with
  `dealer.edit` (OPs always): sections, vehicles, order; the form fills the id from the vehicle
  you ride or look at. The vehicle is drawn only in the buyer's screen
  (`EntityPreview.showroom`), and other clients skip rendering anyone browsing (`browsing` ids).
  Packets `S2CDealer` / `C2SDealer`, protocol 16.

### 3.17.0 — inside a vehicle (`vehicle/DriverRules`, `client/hud/field/VehicleHud`)
- Every seat of an SBW vehicle (`SbwCompat.isRiding`), not only the driver's: no item use
  (`RightClickItem`), no use on blocks, no breaking, no attacking, no TACZ shoot/melee/reload
  (`compat/TaczDriverRules`); `onLivingAttack` cancels any hand or non-SBW-projectile damage a rider
  still deals. SBW's own projectiles (vehicle weapons) still hit. Client: the use/attack keys are
  cancelled at LOWEST (after SBW's own handlers) and first-person hands are not drawn.
- HUD: the wrist device, ghillie notice, belt and weapon slab are hidden while riding, and vanilla
  hearts/armour/food/air/hotbar/item name are cancelled. `VehicleHud` draws a speedometer (km/h
  from the vehicle's per-tick movement, scale 120/240/480) bottom-middle and a panel bottom-right:
  vehicle name, driver/passenger, health in ten segments (`SbwCompat.health`/`maxHealth`, white
  flash and shake on a hit), fuel when it has a tank (`SbwCompat.energyFraction`).

### 3.16.0 — vehicles in a fight, bullets on vehicles, diplomacy and screens removed
- Faction diplomacy (alliances, wars, bounties, built in 3.15.0) was taken out entirely at the
  user's request; protocol 19. Do not bring it back.
- Vehicle PvP log: the owner under the PvP tag (`CombatTracker`) can neither summon nor store;
  the vehicle itself is tagged for `combatLockSeconds` whenever its health drops
  (`VehicleCombat` polls every summoned vehicle twice a second, so every damage source counts)
  and cannot be stored while tagged (`C2SVehicleAction` STORE).
- Summon spot (`VehicleGarage.findSpawnSpot`): the entity is created first and its own box used;
  the centre stays half the diagonal + 2 blocks from the player, the box (plus a block of
  headroom) must be clear of blocks, of the player's box inflated by 1.5, and of any living
  entity; right, left, ahead, diagonals, behind, moving out a block at a time.
- TACZ bullets on SBW vehicles (`compat/TaczVehicleDamage`, TACZ only): `EntityHurtByGunEvent.Pre`
  against a vehicle is cancelled and `amount × bulletVehicleDamage` (server config)
  is taken (default 0.2 since 3.16.1, migrated by `ConfigMigration` v2) through the vehicle's `onHurt(float, Entity, boolean)`, else `setHealth`, by reflection
  (`SbwCompat.damage`). Untested against SBW - if neither method exists the hit falls back to TACZ.
- Ghillie shimmer: two pale passes half a wave apart, full-bright; self 0.16, others up to 0.15.

### 3.14.1
- Ghillie counts TACZ's crawl (forced swimming pose on land) as low, like a crouch
  (`Ghillie.low`). Shimmer much fainter: 6 blocks, alpha up to 0.07, only a direct stare shows it.
- Vault: empty cells light amber under the cursor (`Slots.OPEN`), rent has no operator bypass.
- Showroom: card models pulled back to fit; each section tab carries a small live picture of its
  first vehicle.

### 3.14.0 — vault screen and rent, showroom rework, ghillie by server list
- Ghillie: hidden players are a server-sent id list (`S2CGhillie`), not the invisible flag - other
  invisibility (another mod's suit, potions) no longer skips the wait. Your own shimmer shows in F5.
- Bag: an attachment selected, carried or hovered sets `Slots.FIT` on every mount it fits.
- Faction vault: `UiPage.chrome()` false for a vault - no tab bar or footer; `VaultView.screen`
  puts the vault (56px cells, tall turn buttons, A/D keys) beside the bag. Page turns slide a
  shutter (`closeShutter`/`openShutter`) instead of the static. Rent: pages after the first cost
  `FactionLevel.RENT_PER_PAGE_DAY` (500) each per real day from the faction bank, plans
  `RENT_DAYS` 1/7/20/30 at `RENT_OFF` 0/5/10/15%; `FactionVault.rentUntil`, enforced in
  `FactionVaultAccess.resolve` and `FactionVaultContainer.stillValid` for everyone, operators
  included (3.14.1); paid with `C2SVaultRent` by leader/deputy. The vault's open buffer now carries rent left, bank, may-pay and level.
- Dealership screen rebuilt: section tabs with counts along the top, the vehicle on the stage, an
  information column, a carousel of cards each with its own live model and ribbons (new this
  week via `Listing.added`, owned, level-locked), a purchase dialog and a success screen
  (balance counting down, coins, `ui.purchase`). Editors toggle edit mode in the corner.

## 12. Open items

| # | Item | State |
|---|---|---|
| 1 | **Vehicle lock HUD does not appear** | **open bug.** The lock itself works; the text does not draw. Position is configurable (`lockHudX` / `lockHudY`) but the user reports it invisible, so suspect the overlay registration or an early return in `VehicleLockHud.render`, not the coordinates |
| 2 | **Backpack visible on the player's back** | not started. The user has Curios installed and `survivorsarsenal` renders from a Curios slot. Mirroring our slot into Curios would render for free **but risks duplication** — the item would exist in our persisted NBT *and* a real Curios slot. The alternative is our own `RenderLayer` on the player renderer, which has no duplication risk but needs the backpack item to have a usable 3D model. **Decide this before writing code** |
| 3 | Rank store polish | the user said to build it well and that they would say what they disliked. No feedback yet |
| 4 | Faction vault UI | model complete (`FactionVault`), nothing reaches it |
| 5 | Faction donations / page purchase | not built |
| 6 | VIP double-XP marker on `LevelHud` | not built — must be subtle, explicitly *not* a banner |
| 7 | Global double-XP announcement | not built — explicitly *not* a boss bar |
| 8 | Real defibrillator | planned: TACZ-style item, charge by rubbing the paddles, release on the body; batteries 100 / 250, 50 per revive, R swaps a random carried battery |
| 9 | Batteries in the bag | planned: stack 1, show `c/max` instead of a count, item background filled white by charge (`ChargeTag` already exists; the belt already draws it) |
| 10 | Real oxygen kit | planned: currently the config placeholder item |
| 11 | **3.9.0 – 3.12.0 untested** | written without a compile in the cloud session (Forge maven blocked). Build locally and send any errors |

**Nothing from 3.5.0 onward has been tested in game by me.** It compiles, the jar is verified clean,
and the mixin applies without error in the log — that is all. Several things shipped broken and were
found by the user; assume the same of anything not explicitly confirmed working.

Confirmed working by the user: the money commands (seen in logs), the mixin applying (no errors).
Confirmed broken and since fixed: the scout skill, the vehicle lock, the `mlum_base` death crash.

---

## 13. Working with this user

- They run a live server. Changes ship to real players.
- They dislike partial delivery reported as complete. **Say plainly what is not done.**
- They will give long multi-item lists. Delivering a few things properly beats delivering all of them
  half-built — they have accepted that trade when it was explained.
- They will reaffirm a decision after pushback. When they do, that is the decision. Proceed.
- They report bugs precisely and will send screenshots and F3 entity dumps. **Read them** — the SBW
  energy API was found from one.
- Verify API signatures against the actual jar rather than from memory. Several bugs here came from
  assuming a method's behaviour. `javap -p -c` on a mod's jar settles arguments in seconds, and did —
  twice, for TACZ and for SBW.
