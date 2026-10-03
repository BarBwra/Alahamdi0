package com.barbwra.mlum;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Config split in two:
 * <ul>
 *   <li>{@code SERVER} - gameplay rules. Forge syncs SERVER configs to connected clients, so both
 *       sides agree on radius, pickup rules and what counts as a gun, and the menus stay in sync.</li>
 *   <li>{@code CLIENT} - purely cosmetic, never synced.</li>
 * </ul>
 * Every value is read through a static accessor that falls back to a hard coded default, because
 * config specs are not loaded yet during early startup and on the main menu.
 */
public final class MlumConfig {

    private MlumConfig() {
    }

    /* ------------------------------------------------------------------ server */

    /** The reload skill's card. Shared with {@link ConfigMigration}, which writes it into old files. */
    static final String ATTACHMENTS_LINE =
            "attachments|تعبئة أسرع|تعبّي سلاحك أسرع.|bolt|{n}+15%{/n}|{n}+30%{/n}|{n}+45%{/n}|500|1500|5000";
    /** The quiet search skill's card, taking the first "coming soon" place. */
    static final String QUIET_LINE =
            "quiet_hands|إيد خفيفة|التفتيش السريع يطيّح أشياء أقل.|steps|{n}40%{/n} صوت|{n}30%{/n} صوت|{n}20%{/n} صوت|500|1500|5000";

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue replaceInventory;
        public final ForgeConfigSpec.BooleanValue replaceInCreative;
        public final ForgeConfigSpec.BooleanValue takeoverChests;
        public final ForgeConfigSpec.BooleanValue takeoverBarrels;
        public final ForgeConfigSpec.BooleanValue takeoverEnderChest;

        public final ForgeConfigSpec.DoubleValue vicinityRadius;
        public final ForgeConfigSpec.IntValue scanIntervalTicks;
        public final ForgeConfigSpec.IntValue maxTracked;
        public final ForgeConfigSpec.BooleanValue allowVicinityPickup;
        public final ForgeConfigSpec.BooleanValue directLoot;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> gunItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> gliderItems;
        public final ForgeConfigSpec.BooleanValue enforceGunSlots;
        public final ForgeConfigSpec.BooleanValue grantQuestItems;
        public final ForgeConfigSpec.ConfigValue<String> claimCommand;

        public final ForgeConfigSpec.IntValue mountUpgradeCost;
        public final ForgeConfigSpec.DoubleValue xpRate;
        public final ForgeConfigSpec.DoubleValue vipXpFactor;
        public final ForgeConfigSpec.DoubleValue globalBoostFactor;
        public final ForgeConfigSpec.IntValue levelBaseCost;
        public final ForgeConfigSpec.IntValue levelGrowth;
        public final ForgeConfigSpec.IntValue pointsPerMonster;
        public final ForgeConfigSpec.IntValue playerKillPenalty;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> minePoints;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> levelRewards;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> safeZones;
        public final ForgeConfigSpec.BooleanValue safeZoneBlockPvp;
        public final ForgeConfigSpec.BooleanValue safeZoneRepelMobs;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> vehicleDisplay;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> summonDimensions;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> summonBlacklist;
        public final ForgeConfigSpec.IntValue combatLockSeconds;
        public final ForgeConfigSpec.IntValue summonCooldownSeconds;
        public final ForgeConfigSpec.BooleanValue ownerOnlyDriver;
        public final ForgeConfigSpec.IntValue maxOwnedVehicles;

        public final ForgeConfigSpec.IntValue factionCreateCost;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> skills;
        public final ForgeConfigSpec.IntValue maxedSkills;
        public final ForgeConfigSpec.IntValue skillRefund;
        public final ForgeConfigSpec.ConfigValue<String> skillCommand;
        public final ForgeConfigSpec.BooleanValue menuGuard;
        public final ForgeConfigSpec.IntValue menuGuardRadius;
        public final ForgeConfigSpec.BooleanValue infiniteVehicleEnergy;

        public final ForgeConfigSpec.BooleanValue lootSearch;
        public final ForgeConfigSpec.DoubleValue lootSeconds;
        public final ForgeConfigSpec.DoubleValue lootFastSeconds;
        public final ForgeConfigSpec.DoubleValue lootNoiseChance;
        public final ForgeConfigSpec.DoubleValue lootNoisePause;
        public final ForgeConfigSpec.IntValue lootNoiseRadius;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> lootIgnoreBlocks;

        public final ForgeConfigSpec.BooleanValue downed;
        public final ForgeConfigSpec.IntValue downedSeconds;
        public final ForgeConfigSpec.IntValue reviveSeconds;
        public final ForgeConfigSpec.DoubleValue reviveRange;
        public final ForgeConfigSpec.DoubleValue reviveHealth;
        public final ForgeConfigSpec.IntValue reviveWeakSeconds;
        public final ForgeConfigSpec.IntValue redownWindowSeconds;
        public final ForgeConfigSpec.IntValue redownSeconds;
        public final ForgeConfigSpec.BooleanValue playersFinishDowned;
        public final ForgeConfigSpec.IntValue giveUpSeconds;
        public final ForgeConfigSpec.IntValue distressSeconds;
        public final ForgeConfigSpec.IntValue distressCooldownSeconds;
        public final ForgeConfigSpec.ConfigValue<String> defibItem;
        public final ForgeConfigSpec.IntValue defibCapacity;
        public final ForgeConfigSpec.IntValue defibCost;
        public final ForgeConfigSpec.ConfigValue<String> oxygenItem;
        public final ForgeConfigSpec.IntValue oxygenSeconds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ranks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> moneyPacks;
        public final ForgeConfigSpec.ConfigValue<String> storeNote;
        public final ForgeConfigSpec.IntValue configVersion;
        public final ForgeConfigSpec.BooleanValue camouflage;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> greenSuit;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> snowSuit;

        Server(ForgeConfigSpec.Builder b) {
            configVersion = b
                    .comment("Leave this alone. The mod uses it to bring an old file's values up to date",
                            "once, when a new version changes a default you never edited.")
                    .defineInRange("configVersion", 0, 0, 1000);
            b.comment("Which vanilla screens the tactical UI takes over.").push("takeover");
            replaceInventory = b
                    .comment("Replace the vanilla player inventory screen (the inventory key).",
                            "Hold SHIFT while pressing the inventory key to open the vanilla screen anyway.")
                    .define("replaceInventory", true);
            replaceInCreative = b
                    .comment("Creative mode keeps the vanilla item picker by default, because the",
                            "tactical screen has no creative tabs. Turn this on to force it there too.")
                    .define("replaceInCreative", false);
            takeoverChests = b
                    .comment("Route chests and trapped chests through the tactical screen (single = 3 rows, double = 6 rows).")
                    .define("takeoverChests", true);
            takeoverBarrels = b.define("takeoverBarrels", true);
            takeoverEnderChest = b.define("takeoverEnderChest", true);
            b.pop();

            b.comment("Ground item scanner feeding the vicinity panel.").push("vicinity");
            vicinityRadius = b
                    .comment("Radius in blocks around the player that is scanned for dropped items.")
                    .defineInRange("radius", 4.0D, 1.0D, 16.0D);
            scanIntervalTicks = b
                    .comment("Ticks between two scans while the screen is open. 10 = twice per second.",
                            "Only players with the tactical screen open are ever scanned.")
                    .defineInRange("scanIntervalTicks", 10, 1, 100);
            maxTracked = b
                    .comment("Hard cap on how many ground items are tracked per player (the list scrolls).")
                    .defineInRange("maxTracked", 64, 8, 256);
            allowVicinityPickup = b.define("allowVicinityPickup", true);
            directLoot = b
                    .comment("Left clicking a ground item sends it straight to the inventory instead of onto the cursor.")
                    .define("directLoot", true);
            b.pop();

            b.comment("The two weapon slots (vanilla hotbar indices 0 and 1).").push("weapons");
            gliderItems = b
                    .comment("Item ids the glider cell accepts. Defaults are the five paragliders",
                            "from the vc_gliders mod. Matched by id so this mod never has to compile",
                            "against a glider mod, and swapping to another one is a config edit.")
                    .defineList("gliderItems",
                            List.of("vc_gliders:paraglider_wood",
                                    "vc_gliders:paraglider_iron",
                                    "vc_gliders:paraglider_gold",
                                    "vc_gliders:paraglider_diamond",
                                    "vc_gliders:paraglider_netherite"),
                            entry -> entry instanceof String);
            gunItems = b
                    .comment("Item ids always accepted by the weapon slots.",
                            "TACZ puts every kinetic gun on one item and stores the model in NBT,",
                            "so this one entry covers the whole gun catalogue.")
                    .defineList("allowedItems",
                            List.of("tacz:modern_kinetic_gun"),
                            entry -> entry instanceof String);
            enforceGunSlots = b
                    .comment("Push non-weapons that land in hotbar slots 0-1 (from a pickup, for example)",
                            "back into the rest of the inventory. Leave this on - the weapon slots are",
                            "genuine hotbar slots, which is what makes keys 1/2 and the scroll wheel work,",
                            "so vanilla will occasionally try to drop loot into them.")
                    .define("enforceGunSlots", true);
            b.pop();


            b.comment("Quest reward claiming.").push("quest");
            grantQuestItems = b
                    .comment("Give the reward items shown on the quest card when the player claims it.",
                            "The card draws those items, so not handing them over made the board lie.",
                            "Turn this off only if a Skript command below already grants them.")
                    .define("grantRewardItems", true);
            claimCommand = b
                    .comment("Run from the server console when a player presses Claim on a finished",
                            "quest. %player% and %line% are substituted; nothing else is.",
                            "This mod hands out nothing - point this at a Skript command and let",
                            "Skript decide what the reward actually is. Blank disables the button.")
                    .define("claimCommand", "mlum_claim %player% %line%");
            b.pop();


            b.comment("Progression. This IS vanilla experience - the mod no longer keeps a second",
                            "copy of your level beside it. /xp, enchanting and Skript all read and",
                            "write the same number the game has always used.",
                            "",
                            "Difficulty comes from the rate below, not from a custom curve, so",
                            "enchanting costs still behave exactly as players expect.")
                    .push("level");
            xpRate = b
                    .comment("Multiplies ALL experience the world gives you. Below 1.0 is harder.",
                            "0.35 means a mob worth 10 xp in vanilla gives you about 3.",
                            "This is applied in one place, so it covers mobs, ores, furnaces,",
                            "breeding and bottles - everything.")
                    .defineInRange("xpRate", 0.35D, 0.01D, 10.0D);
            vipXpFactor = b
                    .comment("Permanent multiplier for VIP players, on top of xpRate.",
                            "Granted with: /mlum level vip <player> true")
                    .defineInRange("vipXpFactor", 2.0D, 1.0D, 100.0D);
            globalBoostFactor = b
                    .comment("Default multiplier for the server-wide boost when no factor is given.",
                            "Started with: /mlum level boost <seconds>",
                            "Stacks with VIP, so a VIP during a global 2x earns 4x the base rate.")
                    .defineInRange("globalBoostFactor", 2.0D, 1.0D, 100.0D);
            levelBaseCost = b
                    .comment("Unused since the rewrite - vanilla's own level curve is used instead.",
                            "Left in place so an existing config file does not error on load.")
                    .defineInRange("baseCost", 50, 1, 100000);
            levelGrowth = b
                    .comment("Unused since the rewrite. See baseCost.")
                    .defineInRange("growth", 25, 0, 100000);
            pointsPerMonster = b
                    .comment("Points for killing a hostile mob.")
                    .defineInRange("pointsPerMonster", 5, 0, 10000);
            playerKillPenalty = b
                    .comment("Points LOST for killing another player. Never drops you below zero.")
                    .defineInRange("playerKillPenalty", 25, 0, 100000);
            minePoints = b
                    .comment("Points awarded for breaking a block, as block-id=points.",
                            "Anything not listed awards nothing.")
                    .defineList("minePoints", List.of(
                                    "minecraft:iron_ore=2",
                                    "minecraft:deepslate_iron_ore=2",
                                    "minecraft:gold_ore=3",
                                    "minecraft:deepslate_gold_ore=3",
                                    "minecraft:diamond_ore=10",
                                    "minecraft:deepslate_diamond_ore=10",
                                    "minecraft:ancient_debris=15"),
                            entry -> entry instanceof String);
            levelRewards = b
                    .comment("The reward track, one per line, as level|icon|description|command.",
                            "  level       - the level that unlocks it.",
                            "  icon        - any item id, drawn on the track card.",
                            "  description - shown under the icon. May be Arabic.",
                            "  command     - run from console the moment the level is reached.",
                            "                %player% is substituted. Leave blank for a display-only tier.",
                            "",
                            "Example:",
                            "    rewards = [\"5|minecraft:diamond|لما توصل هنا تصير تقدر تاخذ دبل الدايموند|mwh admin vip %player% true\"]",
                            "",
                            "The command is the whole reward mechanism - this mod hands out nothing",
                            "itself, exactly like the quest claim hook. Point it at Skript and let",
                            "Skript decide what the reward actually is.")
                    .defineList("rewards", List.of(
                                    "5|minecraft:iron_ingot|أول مكافأة - استمر|",
                                    "10|minecraft:diamond|وصلت للمستوى العاشر|",
                                    "20|minecraft:netherite_ingot|لاعب محترف|"),
                            entry -> entry instanceof String);
            b.pop();

            b.comment("Safe areas. Inside one, players cannot hurt each other and mobs are kept out.")
                    .push("safearea");
            safeZones = b
                    .comment("One zone per line, as name,x,y,z,radius[,height].",
                            "  name   - shown in the entering/leaving popup. May be Arabic.",
                            "  x,y,z  - the centre.",
                            "  radius - horizontal radius in blocks.",
                            "  height - optional; how far above and below the centre the zone reaches.",
                            "           Defaults to 64, which is deliberately generous.",
                            "",
                            "The shape is a CYLINDER, not a sphere: horizontal distance decides whether",
                            "you are inside, and height only cuts it off far above and below. A sphere",
                            "would drop players out of a safe zone by climbing onto a roof.",
                            "",
                            "Example - a 40 block safe spawn:",
                            "    zones = [\"المنطقة الآمنة,0,64,0,40\"]",
                            "The name may be omitted, leaving x,y,z,radius. Malformed lines are skipped",
                            "and logged rather than throwing.")
                    .defineList("zones", List.of(), entry -> entry instanceof String);
            safeZoneBlockPvp = b
                    .comment("Cancel all player-versus-player damage when either side is inside a zone.",
                            "Covers melee, TACZ gunfire and anything else a player is the source of -",
                            "including a shot fired from outside at someone standing inside.")
                    .define("blockPvp", true);
            safeZoneRepelMobs = b
                    .comment("Deny mob spawns inside a zone and push living entities back out of it.",
                            "Nothing is ever deleted, and anything carrying a player passenger is left",
                            "alone so a rider is never teleported off their own mount.")
                    .define("repelMobs", true);
            b.pop();

            b.comment("Mounts. A summoned horse is tamed, saddled and owner-locked automatically.")
                    .push("mount");
            mountUpgradeCost = b
                    .comment("Base price of one mount upgrade level, in the warehouse currency item.",
                            "The price is multiplied by the level being bought, so with 2000 the",
                            "first level costs 2000 and the fifth costs 10000.")
                    .defineInRange("upgradeCost", 2000, 0, 10000000);
            b.pop();

            b.comment("Vehicles. Ownership is granted only by /VehicleMenu.").push("vehicle");
            vehicleDisplay = b
                    .comment("How each vehicle looks in the garage, one per line:",
                            "    entityId | image | subtitle | STAT=value, STAT=value",
                            "",
                            "  entityId - must match the id the vehicle was granted with.",
                            "  image    - a texture path, e.g. mlum:textures/vehicle/z10.png",
                            "             Blank, missing, or a file that will not load falls back",
                            "             to a drawn silhouette picked from the name. That fallback",
                            "             is permanent, not a placeholder - a vehicle with no",
                            "             artwork still looks like a vehicle.",
                            "  subtitle - one line under the name. May be Arabic.",
                            "  stats    - any label=value pairs you like; they are printed as rows.",
                            "",
                            "This replaced the 3D entity preview. A data-driven vehicle mod hands",
                            "EntityType.create a blank entity its renderer cannot draw, which is why",
                            "every ywzj_vehicle failed and only the Horse worked. An image cannot",
                            "fail that way.",
                            "",
                            "The texture can live in this mod jar or in a resource pack - the path",
                            "resolves the same either way, so moving the artwork later is a change",
                            "of file location and never of config.")
                    .defineList("display", List.of(
                                    "ywzj_vehicle:m1a2|mlum:textures/vehicle/m1a2.png|دبابة قتال رئيسية|المقاعد=3, السرعة=65",
                                    "ywzj_vehicle:hiace||نقل مدني|المقاعد=6",
                                    "ywzj_vehicle:motorcycle||دراجة سريعة|المقاعد=1",
                                    "minecraft:horse||حصان|المقاعد=1"),
                            entry -> entry instanceof String);
            summonDimensions = b
                    .comment("Dimensions a vehicle may be summoned in. Everywhere else refuses.",
                            "A server's main world is minecraft:overworld.")
                    .defineList("allowedDimensions", List.of("minecraft:overworld"),
                            entry -> entry instanceof String);
            summonBlacklist = b
                    .comment("Areas where summoning is blocked, one per line, as x,y,z,distance.",
                            "distance is the radius in blocks measured from that point.",
                            "Example - no summoning within 100 blocks of 100,4,200:",
                            "    blacklistZones = [\"100,4,200,100\"]",
                            "Anything that is not four numbers is ignored and logged.")
                    .defineList("blacklistZones", List.of(), entry -> entry instanceof String);
            combatLockSeconds = b
                    .comment("How long after PvP damage a player cannot summon or despawn a vehicle.",
                            "This is the 'cannot activate in pvp' lock. 0 disables it.")
                    .defineInRange("combatLockSeconds", 60, 0, 3600);
            summonCooldownSeconds = b
                    .comment("Minimum gap between two summons.")
                    .defineInRange("summonCooldownSeconds", 30, 0, 3600);
            ownerOnlyDriver = b
                    .comment("Only the player who summoned a vehicle may drive it.",
                            "Everyone else can still ride along as a passenger; a non-owner who takes",
                            "the driver seat is put back out on foot. Operators are exempt.",
                            "Vehicles not handed out by this mod are unaffected.")
                    .define("ownerOnlyDriver", true);
            maxOwnedVehicles = b
                    .comment("How many vehicles one player may own at once.")
                    .defineInRange("maxOwned", 24, 1, 64);
            b.pop();

            b.comment("Factions (المنظمة).").push("faction");
            factionCreateCost = b
                    .comment("Price of founding a faction, in the same currency item the warehouse",
                            "uses - see the warehouse config's moneyItem. Charged only on success,",
                            "and never refunded when the faction is later disbanded.",
                            "0 makes founding free.")
                    .defineInRange("createCost", 5000, 0, 10000000);
            b.pop();

            b.comment("Skills (المهارات): permanent perks bought with server money in the skills tab.",
                    "The mod sells them and remembers the levels; what a level DOES is yours to",
                    "decide - buyCommand runs from the console on every purchase, so Skript can",
                    "apply the effect. Levels are also set with /mlum_inventory skill.").push("skills");
            skills = b
                    .comment("One skill per line, as",
                            "    id|name|description|icon|level1|level2|level3|price1|price2|price3",
                            "  id          - a short English word, used by commands and buyCommand.",
                            "  name, description - shown on the card. May be Arabic.",
                            "  icon        - steps, eye, cleaver, blade, cross, run, coins, heart, bolt,",
                            "                bag, quest, rank, car, flag, pin, warn, lock, check.",
                            "  level1..3   - what each level gives. Wrap numbers in {n}..{/n} to draw",
                            "                them in the number font, e.g. {n}+10%{/n}.",
                            "  price1..3   - the price of reaching that level.")
                    .defineList("skills", List.of(
                                    ATTACHMENTS_LINE,
                                    "scout|الباحث|تشوف الصناديق من ورا الجدران، وبالمستوى الثالث تعرف الفاضي منها.|eye|{n}8{/n} بلوك|{n}12{/n} بلوك|{n}16{/n} بلوك|500|1500|5000",
                                    "butcher|الجزار|الزومبي يطيح لحم أكثر.|cleaver|{n}+10%{/n}|{n}+20%{/n}|{n}+35%{/n}|500|1500|5000",
                                    "blade_master|السلاح اليدوي|القتل بسلاح يدوي ينزل أغراض أكثر من الزومبي والوحوش.|blade|{n}+15%{/n}|{n}+30%{/n}|{n}+50%{/n}|500|1500|5000",
                                    "medic|المسعف|كل شي يعالجك يعالج أكثر.|cross|{n}+15%{/n}|{n}+30%{/n}|{n}+50%{/n}|500|1500|5000",
                                    QUIET_LINE,
                                    "soon_2|قادم قريباً|مهارة جديدة تنزل مع تحديث جاي.|lock|—|—|—|0|0|0|true",
                                    "soon_3|قادم قريباً|مهارة جديدة تنزل مع تحديث جاي.|lock|—|—|—|0|0|0|true"),
                            entry -> entry instanceof String);
            maxedSkills = b
                    .comment("How many skills one player may take to the top level.")
                    .defineInRange("maxedSkills", 3, 0, 64);
            skillRefund = b
                    .comment("What it costs to drop a skill back to level 0 and free a maxed slot.",
                            "The money spent on the levels is NOT returned - this is a fee, not a refund.",
                            "Set to 0 to make dropping a skill free.")
                    .defineInRange("refundCost", 2000, 0, Integer.MAX_VALUE);
            skillCommand = b
                    .comment("Run from the console after every purchase, every drop, and every",
                            "/mlum_inventory skill set. %player%, %skill% and %level% are substituted.",
                            "Blank runs nothing - which is the default now, because the skills that",
                            "ship with the mod apply their own effects in code.")
                    .define("buyCommand", "");
            b.pop();

            b.comment("Menu guard (حماية المأفكي): a player reading a menu is not a target.",
                    "Only while nothing is already on top of them - see menuGuardRadius - so it",
                    "protects someone who stepped away, never someone hiding mid-fight.").push("menu_guard");
            menuGuard = b
                    .comment("Stop hostile mobs picking a player who has one of this mod's menus open.")
                    .define("enabled", true);
            menuGuardRadius = b
                    .comment("If a hostile is already this close when it tries to target them, the",
                            "guard does not apply and the fight carries on as normal.")
                    .defineInRange("engagedRadius", 10, 0, 64);
            infiniteVehicleEnergy = b
                    .comment("Summoned Superb Warfare vehicles come out with a full tank and are",
                            "topped back up while anyone is riding, so they never need refuelling.",
                            "Turn off to make fuel a real resource again.")
                    .define("infiniteVehicleEnergy", true);
            b.pop();

            b.comment("Searching (التفتيش): opening anything that stores items takes a moment.",
                    "One right click starts it. Hold Shift during it to search fast, at the risk of",
                    "knocking something over: a noise that stalls the search and brings zombies.",
                    "The quiet_hands skill lowers that risk by 10 points a level.").push("loot_search");
            lootSearch = b
                    .comment("Off opens every container instantly, the vanilla way.")
                    .define("enabled", true);
            lootSeconds = b
                    .comment("How long a normal search takes.")
                    .defineInRange("searchSeconds", 1.5D, 0.0D, 30.0D);
            lootFastSeconds = b
                    .comment("How long a search takes with Shift held.")
                    .defineInRange("fastSeconds", 0.75D, 0.0D, 30.0D);
            lootNoiseChance = b
                    .comment("The chance a fast search makes a noise, 0 to 1.")
                    .defineInRange("noiseChance", 0.5D, 0.0D, 1.0D);
            lootNoisePause = b
                    .comment("How long the noise stalls the search, in seconds.")
                    .defineInRange("noisePauseSeconds", 0.5D, 0.0D, 10.0D);
            lootNoiseRadius = b
                    .comment("Zombies and other hostiles this close hear the noise and come for you.")
                    .defineInRange("noiseRadius", 16, 0, 64);
            lootIgnoreBlocks = b
                    .comment("Block ids that open instantly and get no marker, e.g. a modded machine",
                            "that happens to hold items. Furnaces, hoppers, dispensers, brewing stands",
                            "and the like are already left alone.")
                    .defineList("ignoreBlocks", List.of(), entry -> entry instanceof String);
            b.pop();

            b.comment("Downed (الإصابة): a killing blow puts a player on the ground instead.",
                    "They can be revived by anyone or looted, and die for real when the time runs out.").push("downed");
            downed = b
                    .comment("Off is vanilla death.")
                    .define("enabled", true);
            downedSeconds = b
                    .comment("How long a downed player lasts before they bleed out.")
                    .defineInRange("bleedOutSeconds", 360, 5, 3600);
            reviveSeconds = b
                    .comment("How long F has to be held to revive someone with nothing in hand.")
                    .defineInRange("reviveSeconds", 10, 1, 120);
            reviveRange = b
                    .comment("How close a reviver or looter has to be, in blocks.")
                    .defineInRange("reviveRange", 3.0D, 1.0D, 8.0D);
            reviveHealth = b
                    .comment("The health a revived player comes back with. 20 is full.")
                    .defineInRange("reviveHealth", 6.0D, 1.0D, 20.0D);
            reviveWeakSeconds = b
                    .comment("Slowness and weakness after being revived, in seconds. 0 for none.")
                    .defineInRange("weakSeconds", 10, 0, 300);
            redownWindowSeconds = b
                    .comment("Going down again this soon after a revive uses the shorter timer below.")
                    .defineInRange("redownWindowSeconds", 120, 0, 3600);
            redownSeconds = b
                    .comment("The bleed-out time for a player downed again inside that window.")
                    .defineInRange("redownSeconds", 60, 5, 3600);
            playersFinishDowned = b
                    .comment("Another player's attack finishes a downed player. Zombies always ignore them.")
                    .define("playersCanFinish", true);
            giveUpSeconds = b
                    .comment("How long a downed player holds F to give up and die now. E calls for help.")
                    .defineInRange("giveUpSeconds", 3, 1, 30);
            distressSeconds = b
                    .comment("How long a distress call stays on the faction's screens.")
                    .defineInRange("distressSeconds", 30, 5, 300);
            distressCooldownSeconds = b
                    .comment("How often a downed player may call for help.")
                    .defineInRange("distressCooldownSeconds", 60, 5, 600);
            defibItem = b
                    .comment("The item that works as the defibrillator for now: look at a downed player",
                            "and use it to revive them at once. A placeholder until the real one exists.")
                    .define("defibItem", "minecraft:blaze_rod");
            defibCapacity = b
                    .comment("The charge a defibrillator holds.")
                    .defineInRange("defibCapacity", 250, 1, 100000);
            defibCost = b
                    .comment("The charge one revive spends.")
                    .defineInRange("defibCost", 50, 1, 100000);
            oxygenItem = b
                    .comment("The item that works as the oxygen kit for now: hold use on a downed player.",
                            "Spent after one revive. A placeholder until the real one exists.")
                    .define("oxygenItem", "minecraft:phantom_membrane");
            oxygenSeconds = b
                    .comment("How long the oxygen kit has to be held on a downed player.")
                    .defineInRange("oxygenSeconds", 5, 1, 60);
            b.pop();

            b.comment("Camouflage (التمويه): a full ghillie suit hides its wearer completely - body, armour,",
                    "what they hold, their shadow and their name - while they are in the right cover.",
                    "Green: leaves, grass, crops, bushes, flowers and vines, or crouched on grass or moss.",
                    "Snow: snow, powder snow and ice.").push("camouflage");
            camouflage = b
                    .comment("Off: the suits are ordinary armour.")
                    .define("enabled", true);
            greenSuit = b
                    .comment("The green suit, all four pieces. Every one must be worn.")
                    .defineList("greenSuit", List.of(
                                    "survival_instinct:guillie_helmet",
                                    "survival_instinct:guillie_chestplate",
                                    "survival_instinct:guillie_leggings",
                                    "survival_instinct:guillie_boots"),
                            entry -> entry instanceof String);
            snowSuit = b
                    .comment("The snow suit, all four pieces. Every one must be worn.")
                    .defineList("snowSuit", List.of(
                                    "survival_instinct:artic_guillie_helmet",
                                    "survival_instinct:artic_guillie_chestplate",
                                    "survival_instinct:artic_guillie_leggings",
                                    "survival_instinct:artic_guillie_boots"),
                            entry -> entry instanceof String);
            b.pop();

            b.comment("Ranks (الرتب): the ladder shown beside the wallet, and the store screen.",
                    "Nothing in this section charges anyone - ranks are sold outside the game and",
                    "handed over with /mlum_inventory rank set. This is the shop window.").push("ranks");
            ranks = b
                    .comment("The rank ladder, lowest first. One per line, as",
                            "    id|name|colour|price|blurb|perk;perk;perk",
                            "  id     - short English word. What /mlum_inventory rank set takes.",
                            "  colour - RRGGBB hex, no #.",
                            "  price  - free text, shown in the store screen. Ranks are sold outside",
                            "           the game; nothing here charges anyone. Leave blank to hide",
                            "           the price line entirely.",
                            "  blurb  - one line under the rank's name.",
                            "  perks  - semicolon separated, one bullet each. Optional.")
                    .defineList("ranks", List.of(
                                    "default|Default|9e9b8a||الرتبة الأساسية لكل لاعب.|",
                                    "vip|VIP|f0a93b||ترقية أولى بمزايا خفيفة.|",
                                    "vip_plus|VIP+|ffc857||كل مزايا VIP وزيادة.|",
                                    "mvp|MVP|7cc25a||رتبة متقدمة للاعبين الدائمين.|",
                                    "mvp_plus|MVP+|4ea8e8||أعلى رتبة تنشرى.|",
                                    "co_owner|Co-Owner|b57ce8||إدارة السيرفر.|",
                                    "owner|Owner|e6cfa1||صاحب السيرفر.|"),
                            entry -> entry instanceof String);
            moneyPacks = b
                    .comment("What the money button lists. One per line, as",
                            "    label|amount|price",
                            "  amount - how much in-game money the pack gives, for display only.",
                            "  price  - free text again. Nothing here charges anyone.")
                    .defineList("moneyPacks", List.of(), entry -> entry instanceof String);
            storeNote = b
                    .comment("A line under both store lists - where to actually buy. Blank hides it.")
                    .define("storeNote", "");
            b.pop();
        }
    }

    /* ------------------------------------------------------------------ client */

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showPlayerModel;
        public final ForgeConfigSpec.ConfigValue<String> accentColor;
        public final ForgeConfigSpec.IntValue panelOpacity;
        public final ForgeConfigSpec.IntValue backdropOpacity;
        public final ForgeConfigSpec.DoubleValue fillFraction;
        public final ForgeConfigSpec.BooleanValue showWatermark;
        public final ForgeConfigSpec.BooleanValue showVehicleModel;

        public final ForgeConfigSpec.BooleanValue opOnlyHitboxes;
        public final ForgeConfigSpec.BooleanValue attachmentSlots;
        public final ForgeConfigSpec.BooleanValue firearmCard;
        public final ForgeConfigSpec.BooleanValue fieldHud;
        public final ForgeConfigSpec.BooleanValue lootMarkers;
        public final ForgeConfigSpec.IntValue lootMarkerRange;
        public final ForgeConfigSpec.ConfigValue<String> fieldHudAccent;
        public final ForgeConfigSpec.DoubleValue fieldHudScale;

        public final ForgeConfigSpec.BooleanValue animationsEnabled;
        public final ForgeConfigSpec.DoubleValue animationSpeed;

        public final ForgeConfigSpec.DoubleValue scrimStrength;
        public final ForgeConfigSpec.IntValue zombieAlertRadius;
        public final ForgeConfigSpec.BooleanValue lightMode;
        public final ForgeConfigSpec.BooleanValue uiSounds;
        public final ForgeConfigSpec.IntValue lockHudX;
        public final ForgeConfigSpec.IntValue lockHudY;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("The menus (bag, quests, level, skills, vehicles, faction).").push("ui");
            scrimStrength = b
                    .comment("How dark the shade between the world and the menus is. 1.0 is the design:",
                            "the bag keeps a clear window in the middle so you can see what is behind",
                            "you, every other menu is a flat shade. 0 turns the shade off, 1.5 is darker.")
                    .defineInRange("scrim_strength", 1.0D, 0.0D, 1.5D);
            zombieAlertRadius = b
                    .comment("While a menu is open, a hostile mob this close (in blocks) that is not in",
                            "front of you lights the edge of the screen on its side. 0 turns it off.")
                    .defineInRange("zombie_alert_radius", 12, 0, 48);
            lightMode = b
                    .comment("For weak computers: no static between menus, no shine or sparks on the",
                            "wallet, no pulsing. Everything still works and looks the same at rest.")
                    .define("light_mode", false);
            lockHudX = b
                    .comment("Where the vehicle's lock line sits, in GUI pixels from the left edge.")
                    .defineInRange("vehicleLockHudX", 8, 0, 4096);
            lockHudY = b
                    .comment("And how far up from the bottom. The default puts it one line above the",
                            "seat list Superb Warfare draws - that list is positioned by its own",
                            "anchor system, so this cannot be read from it and is tuned by eye.",
                            "Raise the number to move the line up.")
                    .defineInRange("vehicleLockHudY", 126, 0, 4096);
            uiSounds = b
                    .comment("The static between menus, the coin and the pick-up ticks.")
                    .define("sounds", true);
            b.pop();

            b.comment("Look and feel of the tactical screen.").push("theme");
            accentColor = b
                    .comment("Accent colour as RRGGBB hex. The whole UI recolours from this one value.",
                            "The default F0A93B is the amber the bag design was built around; it is the",
                            "structural colour (headings, selection, live tab) and is deliberately",
                            "different from the body text, so a wildly different hue here will read as",
                            "a recolour rather than as a theme.")
                    .define("accentColor", "F0A93B");
            panelOpacity = b
                    .comment("Scales the design's own panel transparency, 0 (invisible) to 255 (as designed).",
                            "The panels are already see-through by design - 0.86 for a panel, 0.88 for a",
                            "card - because you must be able to spot a zombie walking up behind the bag.",
                            "255 means exactly the designed look; lower makes them thinner still.")
                    .defineInRange("panelOpacity", 255, 0, 255);
            backdropOpacity = b
                    .comment("No longer used. Nothing darkens the world behind the menus any more - the",
                            "player must be able to see what is coming while a menu is open. Kept so an",
                            "existing config file still loads.")
                    .defineInRange("backdropOpacity", 40, 0, 64);
            fillFraction = b
                    .comment("How much of the window the UI fills. 1.0 = edge to edge.",
                            "The canvas is 16:9, so 1.0 fills a 16:9 monitor exactly, at any GUI Scale setting.")
                    .defineInRange("fillFraction", 1.0D, 0.4D, 1.0D);
            b.pop();

            b.comment("Individual widgets.").push("widgets");
            showPlayerModel = b.define("showPlayerModel", true);
            showWatermark = b.define("showWatermark", true);
            showVehicleModel = b
                    .comment("Draw the selected vehicle as a real 3D entity in the garage.",
                            "Turn this off if a vehicle mod's renderer misbehaves - the menu stays",
                            "fully usable either way, you just get a name card instead of a model.")
                    .define("showVehicleModel", true);
            b.pop();

            b.comment("The in-game HUD.",
                    "The vanilla HUD is left exactly as it is - all nine hotbar slots, hearts,",
                    "armour, hunger and the experience bar. The only addition is a card in the",
                    "bottom right showing the two firearms.").push("hud");
            opOnlyHitboxes = b
                    .comment("Take F3+B (entity hitboxes) away from non-operators.",
                            "It outlines every entity through walls, which on a survival server",
                            "is a wallhack the game ships with. Operators keep it.")
                    .define("opOnlyHitboxes", true);
            attachmentSlots = b
                    .comment("Fit TACZ attachments from the inventory instead of its own Z screen.",
                            "Six mounting points per firearm - scope, muzzle, magazine, grip,",
                            "stock and laser - shown beside each gun card.",
                            "This also CANCELS TACZ's refit screen: two ways to install the same",
                            "attachment that disagree about what is fitted is worse than one.",
                            "Off gives TACZ its Z screen back and hides these slots.")
                    .define("attachmentSlots", true);
            firearmCard = b
                    .comment("Draw both firearms in the bottom right: the one in hand large with its",
                            "round count, the other as a dim strip underneath.",
                            "This also hides TACZ's own ammo readout, which occupies the same corner.",
                            "Off gives TACZ its readout back, with no other change.")
                    .define("firearmCard", true);
            fieldHud = b
                    .comment("The field HUD: a wrist device bottom left (heartbeat, health inside the",
                            "armour shield, food, level), the belt in the middle instead of the hotbar,",
                            "a weapon panel bottom right and a compass at the top. It replaces the",
                            "vanilla hearts, armour, hunger, hotbar and experience bar, the level bar",
                            "and the firearm card. Off brings every one of those back exactly as before.")
                    .define("fieldHud", true);
            fieldHudAccent = b
                    .comment("The field HUD's colour as RRGGBB hex. A8C66C is the olive it was designed in.")
                    .define("fieldHudAccent", "A8C66C");
            fieldHudScale = b
                    .comment("How big the field HUD is drawn, on top of the GUI Scale setting. 1.0 is the",
                            "original size; each panel grows from its own corner of the screen.")
                    .defineInRange("fieldHudScale", 1.15D, 0.75D, 2.0D);
            lootMarkers = b
                    .comment("Corner marks and a mouse icon on containers you can see and search.")
                    .define("lootMarkers", true);
            lootMarkerRange = b
                    .comment("How far away a container gets its marker, in blocks.")
                    .defineInRange("lootMarkerRange", 10, 2, 32);
            b.pop();

            b.comment("Motion. Everything here is cosmetic and can be turned off wholesale.").push("animations");
            animationsEnabled = b
                    .comment("Panel entry, eased hover, sliding selection rings and tweened bars.",
                            "Off draws every one of them in its settled state instead.")
                    .define("enabled", true);
            animationSpeed = b
                    .comment("Multiplies every duration. Above 1.0 is faster, below is slower.")
                    .defineInRange("speed", 1.0D, 0.25D, 3.0D);
            b.pop();
        }
    }

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();

        final Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    /* ------------------------------------------------------- safe accessors */

    private static boolean serverReady() {
        return SERVER_SPEC.isLoaded();
    }

    private static boolean clientReady() {
        return CLIENT_SPEC.isLoaded();
    }

    public static boolean replaceInventory() {
        return !serverReady() || SERVER.replaceInventory.get();
    }

    public static boolean replaceInCreative() {
        return serverReady() && SERVER.replaceInCreative.get();
    }

    public static boolean takeoverChests() {
        return !serverReady() || SERVER.takeoverChests.get();
    }

    public static boolean takeoverBarrels() {
        return !serverReady() || SERVER.takeoverBarrels.get();
    }

    public static boolean takeoverEnderChest() {
        return !serverReady() || SERVER.takeoverEnderChest.get();
    }

    public static double vicinityRadius() {
        return serverReady() ? SERVER.vicinityRadius.get() : 4.0D;
    }

    public static int scanIntervalTicks() {
        return serverReady() ? SERVER.scanIntervalTicks.get() : 10;
    }

    public static int maxTracked() {
        return serverReady() ? SERVER.maxTracked.get() : 64;
    }

    public static boolean allowVicinityPickup() {
        return !serverReady() || SERVER.allowVicinityPickup.get();
    }

    public static boolean directLoot() {
        return !serverReady() || SERVER.directLoot.get();
    }

    public static boolean enforceGunSlots() {
        return !serverReady() || SERVER.enforceGunSlots.get();
    }

    private static List<? extends String> gliderRaw;
    private static Set<String> gliderCache = Set.of(
            "vc_gliders:paraglider_wood", "vc_gliders:paraglider_iron",
            "vc_gliders:paraglider_gold", "vc_gliders:paraglider_diamond",
            "vc_gliders:paraglider_netherite");

    /** Cached like the gun list: the check runs on every slot interaction. */
    public static Set<String> gliderItemIds() {
        if (!serverReady()) {
            return gliderCache;
        }
        List<? extends String> raw = SERVER.gliderItems.get();
        if (raw != gliderRaw) {
            gliderRaw = raw;
            gliderCache = new HashSet<>(raw);
        }
        return gliderCache;
    }

    private static List<? extends String> gunItemsRaw;
    private static Set<String> gunItemsCache = Set.of("tacz:modern_kinetic_gun");

    /** Cached because the weapon check runs on every slot interaction. */
    public static Set<String> gunItemIds() {
        if (!serverReady()) {
            return gunItemsCache;
        }
        List<? extends String> raw = SERVER.gunItems.get();
        if (raw != gunItemsRaw) {
            gunItemsRaw = raw;
            gunItemsCache = new HashSet<>(raw);
        }
        return gunItemsCache;
    }


    public static boolean grantQuestItems() {
        return !serverReady() || SERVER.grantQuestItems.get();
    }

    public static String claimCommand() {
        return serverReady() ? SERVER.claimCommand.get().trim() : "mlum_claim %player% %line%";
    }


    private static List<? extends String> dimRaw;
    private static Set<String> dimCache = Set.of("minecraft:overworld");

    /** Dimensions a vehicle may be summoned in. Cached; checked on every summon. */
    public static Set<String> summonDimensions() {
        if (!serverReady()) {
            return dimCache;
        }
        List<? extends String> raw = SERVER.summonDimensions.get();
        if (raw != dimRaw) {
            dimRaw = raw;
            dimCache = new HashSet<>(raw);
        }
        return dimCache;
    }

    /** One blacklisted sphere: no vehicle may be summoned within {@code radius} of this point. */
    public record SummonZone(double x, double y, double z, double radius) {

        public boolean contains(double px, double py, double pz) {
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }

    private static List<? extends String> zoneRaw;
    private static List<SummonZone> zoneCache = List.of();

    /**
     * Parsed {@code x,y,z,distance} entries.
     *
     * <p>Parsed once per config change rather than per summon, and a malformed line is skipped with
     * a warning instead of throwing - one typo in a zone list should not stop every player on the
     * server from using a vehicle.</p>
     */
    public static List<SummonZone> summonBlacklist() {
        if (!serverReady()) {
            return zoneCache;
        }
        List<? extends String> raw = SERVER.summonBlacklist.get();
        if (raw == zoneRaw) {
            return zoneCache;
        }
        zoneRaw = raw;
        List<SummonZone> parsed = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split("\\s*,\\s*");
            if (parts.length != 4) {
                MlumInventory.LOGGER.warn("[{}] ignoring malformed blacklistZones entry '{}' "
                        + "- expected x,y,z,distance", MlumInventory.MODID, entry);
                continue;
            }
            try {
                parsed.add(new SummonZone(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Math.max(0.0D, Double.parseDouble(parts[3].trim()))));
            } catch (NumberFormatException bad) {
                MlumInventory.LOGGER.warn("[{}] ignoring blacklistZones entry '{}' - not four numbers",
                        MlumInventory.MODID, entry);
            }
        }
        zoneCache = List.copyOf(parsed);
        return zoneCache;
    }

    /**
     * One safe area: a cylinder centred on {@code x,z}, cut off {@code height} above and below
     * {@code y}.
     *
     * <p>A cylinder rather than a sphere on purpose. With a sphere, a player standing on a roof
     * inside the zone leaves it - the horizontal distance is fine but the vertical one is not - and
     * "I got shot standing on the spawn wall" is not a bug anyone should have to explain.</p>
     */
    public record SafeZone(String name, double x, double y, double z, double radius, double height) {

        public boolean contains(double px, double py, double pz) {
            double dx = px - x;
            double dz = pz - z;
            if (dx * dx + dz * dz > radius * radius) {
                return false;
            }
            return Math.abs(py - y) <= height;
        }

        /** Nearest point on the boundary at the entity's own height - where a repelled mob lands. */
        public double[] pushOut(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4D) {
                // dead centre: any direction will do, so pick one deterministically
                return new double[]{x + radius + 1.0D, z};
            }
            double scale = (radius + 1.5D) / len;
            return new double[]{x + dx * scale, z + dz * scale};
        }
    }

    private static final double DEFAULT_ZONE_HEIGHT = 64.0D;

    private static List<? extends String> safeRaw;
    private static List<SafeZone> safeCache = List.of();

    /**
     * Parsed safe areas.
     *
     * <p>Reparsed only when the underlying list instance changes, because this is consulted on
     * every damage event and every mob tick. A malformed line is skipped with a warning - one typo
     * must not take PvP protection off the whole server.</p>
     */
    public static List<SafeZone> safeZones() {
        if (!serverReady()) {
            return safeCache;
        }
        List<? extends String> raw = SERVER.safeZones.get();
        if (raw == safeRaw) {
            return safeCache;
        }
        safeRaw = raw;

        List<SafeZone> parsed = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split("\\s*,\\s*");
            // name,x,y,z,radius[,height] - or the same without a name
            boolean named = parts.length == 5 || parts.length == 6;
            if (parts.length < 4 || parts.length > 6) {
                MlumInventory.LOGGER.warn("[{}] ignoring malformed safe zone '{}' "
                        + "- expected name,x,y,z,radius[,height]", MlumInventory.MODID, entry);
                continue;
            }
            try {
                int i = named ? 1 : 0;
                String name = named ? parts[0].trim() : "منطقة آمنة";
                double x = Double.parseDouble(parts[i].trim());
                double y = Double.parseDouble(parts[i + 1].trim());
                double z = Double.parseDouble(parts[i + 2].trim());
                double radius = Math.max(1.0D, Double.parseDouble(parts[i + 3].trim()));
                double height = parts.length == (named ? 6 : 5)
                        ? Math.max(1.0D, Double.parseDouble(parts[i + 4].trim()))
                        : DEFAULT_ZONE_HEIGHT;
                parsed.add(new SafeZone(name, x, y, z, radius, height));
            } catch (NumberFormatException bad) {
                MlumInventory.LOGGER.warn("[{}] ignoring safe zone '{}' - coordinates are not numbers",
                        MlumInventory.MODID, entry);
            }
        }
        safeCache = List.copyOf(parsed);
        return safeCache;
    }

    /** The zone containing this point, or null. */
    public static SafeZone safeZoneAt(double x, double y, double z) {
        for (SafeZone zone : safeZones()) {
            if (zone.contains(x, y, z)) {
                return zone;
            }
        }
        return null;
    }

    public static int mountUpgradeCost() {
        return serverReady() ? SERVER.mountUpgradeCost.get() : 2000;
    }

    /* ---------------------------------------------------------------- faction */

    public static List<? extends String> skillLines() {
        return serverReady() ? SERVER.skills.get() : List.of();
    }

    public static int maxedSkills() {
        return serverReady() ? SERVER.maxedSkills.get() : 3;
    }

    public static int skillRefundCost() {
        return serverReady() ? SERVER.skillRefund.get() : 2000;
    }

    public static boolean menuGuard() {
        return serverReady() ? SERVER.menuGuard.get() : true;
    }

    public static int menuGuardRadius() {
        return serverReady() ? SERVER.menuGuardRadius.get() : 10;
    }

    /* ---- searching ---- */

    public static boolean lootSearch() {
        return !serverReady() || SERVER.lootSearch.get();
    }

    public static double lootSeconds() {
        return serverReady() ? SERVER.lootSeconds.get() : 1.5D;
    }

    public static double lootFastSeconds() {
        return serverReady() ? SERVER.lootFastSeconds.get() : 0.75D;
    }

    public static double lootNoiseChance() {
        return serverReady() ? SERVER.lootNoiseChance.get() : 0.5D;
    }

    public static double lootNoisePause() {
        return serverReady() ? SERVER.lootNoisePause.get() : 0.5D;
    }

    public static int lootNoiseRadius() {
        return serverReady() ? SERVER.lootNoiseRadius.get() : 16;
    }

    public static List<? extends String> lootIgnoredBlocks() {
        return serverReady() ? SERVER.lootIgnoreBlocks.get() : List.of();
    }

    /* ---- downed ---- */

    public static boolean downed() {
        return !serverReady() || SERVER.downed.get();
    }

    public static int downedSeconds() {
        return serverReady() ? SERVER.downedSeconds.get() : 360;
    }

    public static int reviveSeconds() {
        return serverReady() ? SERVER.reviveSeconds.get() : 10;
    }

    public static double reviveRange() {
        return serverReady() ? SERVER.reviveRange.get() : 3.0D;
    }

    public static float reviveHealth() {
        return serverReady() ? SERVER.reviveHealth.get().floatValue() : 6.0F;
    }

    public static int reviveWeakSeconds() {
        return serverReady() ? SERVER.reviveWeakSeconds.get() : 10;
    }

    public static int redownWindowSeconds() {
        return serverReady() ? SERVER.redownWindowSeconds.get() : 120;
    }

    public static int redownSeconds() {
        return serverReady() ? SERVER.redownSeconds.get() : 60;
    }

    public static boolean playersFinishDowned() {
        return !serverReady() || SERVER.playersFinishDowned.get();
    }

    public static int giveUpSeconds() {
        return serverReady() ? SERVER.giveUpSeconds.get() : 3;
    }

    public static int distressSeconds() {
        return serverReady() ? SERVER.distressSeconds.get() : 30;
    }

    public static int distressCooldownSeconds() {
        return serverReady() ? SERVER.distressCooldownSeconds.get() : 60;
    }

    public static String defibItem() {
        return serverReady() ? SERVER.defibItem.get() : "minecraft:blaze_rod";
    }

    public static int defibCapacity() {
        return serverReady() ? SERVER.defibCapacity.get() : 250;
    }

    public static int defibCost() {
        return serverReady() ? SERVER.defibCost.get() : 50;
    }

    public static String oxygenItem() {
        return serverReady() ? SERVER.oxygenItem.get() : "minecraft:phantom_membrane";
    }

    public static int oxygenSeconds() {
        return serverReady() ? SERVER.oxygenSeconds.get() : 5;
    }

    public static boolean infiniteVehicleEnergy() {
        return serverReady() ? SERVER.infiniteVehicleEnergy.get() : true;
    }

    public static List<? extends String> rankLines() {
        return serverReady() ? SERVER.ranks.get() : List.of();
    }

    public static List<? extends String> moneyPackLines() {
        return serverReady() ? SERVER.moneyPacks.get() : List.of();
    }

    public static String storeNote() {
        return serverReady() ? SERVER.storeNote.get() : "";
    }

    public static String skillCommand() {
        return serverReady() ? SERVER.skillCommand.get() : "";
    }

    public static int factionCreateCost() {
        return serverReady() ? SERVER.factionCreateCost.get() : 5000;
    }

    /* ------------------------------------------------------------------ level */

    public static double xpRate() {
        return serverReady() ? SERVER.xpRate.get() : 0.35D;
    }

    public static double vipXpFactor() {
        return serverReady() ? SERVER.vipXpFactor.get() : 2.0D;
    }

    public static double globalBoostFactor() {
        return serverReady() ? SERVER.globalBoostFactor.get() : 2.0D;
    }

    public static int levelBaseCost() {
        return serverReady() ? SERVER.levelBaseCost.get() : 50;
    }

    public static int levelGrowth() {
        return serverReady() ? SERVER.levelGrowth.get() : 25;
    }

    public static int pointsPerMonster() {
        return serverReady() ? SERVER.pointsPerMonster.get() : 5;
    }

    public static int playerKillPenalty() {
        return serverReady() ? SERVER.playerKillPenalty.get() : 25;
    }

    private static List<? extends String> mineRaw;
    private static Map<String, Integer> mineCache = Map.of();

    /** Block id to points. Cached - this is consulted on every block break on the server. */
    public static Map<String, Integer> minePoints() {
        if (!serverReady()) {
            return mineCache;
        }
        List<? extends String> raw = SERVER.minePoints.get();
        if (raw == mineRaw) {
            return mineCache;
        }
        mineRaw = raw;
        Map<String, Integer> parsed = new HashMap<>();
        for (String entry : raw) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                MlumInventory.LOGGER.warn("[{}] ignoring minePoints entry '{}' - expected block=points",
                        MlumInventory.MODID, entry);
                continue;
            }
            try {
                parsed.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException bad) {
                MlumInventory.LOGGER.warn("[{}] ignoring minePoints entry '{}' - points is not a number",
                        MlumInventory.MODID, entry);
            }
        }
        mineCache = Map.copyOf(parsed);
        return mineCache;
    }

    /** One tier of the reward track. {@code command} may be blank for a display-only tier. */
    public record LevelReward(int level, String icon, String description, String command) {
    }

    private static List<? extends String> rewardRaw;
    private static List<LevelReward> rewardCache = List.of();

    /** The reward track, sorted by level so the screen can draw it left to right. */
    public static List<LevelReward> levelRewards() {
        if (!serverReady()) {
            return rewardCache;
        }
        List<? extends String> raw = SERVER.levelRewards.get();
        if (raw == rewardRaw) {
            return rewardCache;
        }
        rewardRaw = raw;

        List<LevelReward> parsed = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length < 3) {
                MlumInventory.LOGGER.warn("[{}] ignoring reward '{}' "
                        + "- expected level|icon|description|command", MlumInventory.MODID, entry);
                continue;
            }
            try {
                parsed.add(new LevelReward(
                        Integer.parseInt(parts[0].trim()),
                        parts[1].trim(),
                        parts[2].trim(),
                        parts.length > 3 ? parts[3].trim() : ""));
            } catch (NumberFormatException bad) {
                MlumInventory.LOGGER.warn("[{}] ignoring reward '{}' - level is not a number",
                        MlumInventory.MODID, entry);
            }
        }
        parsed.sort(java.util.Comparator.comparingInt(LevelReward::level));
        rewardCache = List.copyOf(parsed);
        return rewardCache;
    }

    public static boolean safeZoneBlockPvp() {
        return !serverReady() || SERVER.safeZoneBlockPvp.get();
    }

    public static boolean safeZoneRepelMobs() {
        return !serverReady() || SERVER.safeZoneRepelMobs.get();
    }

    /** One parsed {@code display} line: how a vehicle presents itself in the garage. */
    public record VehicleDisplay(String entityId, String image, String subtitle,
                                 List<String[]> stats) {
    }

    private static List<? extends String> displayRaw;
    private static Map<String, VehicleDisplay> displayCache = Map.of();

    /**
     * The display table, keyed by entity id.
     *
     * <p>A malformed line is skipped with a warning rather than throwing - one typo in a list of
     * thirty vehicles should not empty the whole garage.</p>
     */
    public static Map<String, VehicleDisplay> vehicleDisplays() {
        if (!serverReady()) {
            return displayCache;
        }
        List<? extends String> raw = SERVER.vehicleDisplay.get();
        if (raw == displayRaw) {
            return displayCache;
        }
        displayRaw = raw;
        Map<String, VehicleDisplay> parsed = new HashMap<>();
        for (String line : raw) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 1 || parts[0].isBlank()) {
                MlumInventory.LOGGER.warn("[{}] ignoring malformed vehicle display '{}'",
                        MlumInventory.MODID, line);
                continue;
            }
            List<String[]> stats = new ArrayList<>();
            if (parts.length > 3 && !parts[3].isBlank()) {
                for (String pair : parts[3].split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        stats.add(new String[]{kv[0].trim(), kv[1].trim()});
                    }
                }
            }
            parsed.put(parts[0].trim(), new VehicleDisplay(
                    parts[0].trim(),
                    parts.length > 1 ? parts[1].trim() : "",
                    parts.length > 2 ? parts[2].trim() : "",
                    List.copyOf(stats)));
        }
        displayCache = Map.copyOf(parsed);
        return displayCache;
    }

    /** Just the image paths, which is all {@code VehicleArt} needs. */
    public static Map<String, String> vehicleImages() {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, VehicleDisplay> entry : vehicleDisplays().entrySet()) {
            out.put(entry.getKey(), entry.getValue().image());
        }
        return out;
    }

    public static int combatLockSeconds() {
        return serverReady() ? SERVER.combatLockSeconds.get() : 15;
    }

    public static int summonCooldownSeconds() {
        return serverReady() ? SERVER.summonCooldownSeconds.get() : 30;
    }

    public static boolean ownerOnlyDriver() {
        return !serverReady() || SERVER.ownerOnlyDriver.get();
    }

    public static int maxOwnedVehicles() {
        return serverReady() ? SERVER.maxOwnedVehicles.get() : 24;
    }


    public static boolean showPlayerModel() {
        return !clientReady() || CLIENT.showPlayerModel.get();
    }

    public static boolean showVehicleModel() {
        return !clientReady() || CLIENT.showVehicleModel.get();
    }

    public static boolean showWatermark() {
        return !clientReady() || CLIENT.showWatermark.get();
    }

    public static int panelOpacity() {
        return clientReady() ? CLIENT.panelOpacity.get() : 255;
    }

    public static int backdropOpacity() {
        return clientReady() ? CLIENT.backdropOpacity.get() : 40;
    }

    public static double fillFraction() {
        return clientReady() ? CLIENT.fillFraction.get() : 1.0D;
    }

    public static boolean opOnlyHitboxes() {
        return !clientReady() || CLIENT.opOnlyHitboxes.get();
    }

    public static boolean attachmentSlots() {
        return !clientReady() || CLIENT.attachmentSlots.get();
    }

    public static boolean firearmCard() {
        return !clientReady() || CLIENT.firearmCard.get();
    }

    public static boolean fieldHud() {
        return !clientReady() || CLIENT.fieldHud.get();
    }

    public static boolean lootMarkers() {
        return !clientReady() || CLIENT.lootMarkers.get();
    }

    public static int lootMarkerRange() {
        return clientReady() ? CLIENT.lootMarkerRange.get() : 10;
    }

    private static String fieldAccentRaw;
    private static int fieldAccent = 0xFFA8C66C;

    /** The field HUD's accent as opaque ARGB, re-parsed only when the config line changes. */
    public static boolean camouflage() {
        return !serverReady() || SERVER.camouflage.get();
    }

    public static List<? extends String> greenSuit() {
        return serverReady() ? SERVER.greenSuit.get() : SERVER.greenSuit.getDefault();
    }

    public static List<? extends String> snowSuit() {
        return serverReady() ? SERVER.snowSuit.get() : SERVER.snowSuit.getDefault();
    }

    public static float fieldHudScale() {
        return clientReady() ? CLIENT.fieldHudScale.get().floatValue() : 1.15F;
    }

    public static int fieldHudAccent() {
        if (!clientReady()) {
            return 0xFFA8C66C;
        }
        String raw = CLIENT.fieldHudAccent.get();
        if (!raw.equals(fieldAccentRaw)) {
            fieldAccentRaw = raw;
            try {
                fieldAccent = 0xFF000000 | Integer.parseInt(raw.trim().replace("#", ""), 16);
            } catch (NumberFormatException bad) {
                fieldAccent = 0xFFA8C66C;
            }
        }
        return fieldAccent;
    }

    public static boolean animationsEnabled() {
        return !clientReady() || CLIENT.animationsEnabled.get();
    }

    public static float scrimStrength() {
        return clientReady() ? CLIENT.scrimStrength.get().floatValue() : 1.0F;
    }

    public static int zombieAlertRadius() {
        return clientReady() ? CLIENT.zombieAlertRadius.get() : 12;
    }

    public static boolean lightMode() {
        return clientReady() && CLIENT.lightMode.get();
    }

    public static boolean uiSounds() {
        return !clientReady() || CLIENT.uiSounds.get();
    }

    public static int lockHudX() {
        return clientReady() ? CLIENT.lockHudX.get() : 8;
    }

    public static int lockHudY() {
        return clientReady() ? CLIENT.lockHudY.get() : 126;
    }

    public static float animationSpeed() {
        return clientReady() ? CLIENT.animationSpeed.get().floatValue() : 1.0F;
    }

    /** The bag design's amber. See {@code Theme.AMBER}, which mirrors it client side. */
    private static final int DEFAULT_ACCENT = 0xFFF0A93B;
    private static String accentRaw;
    private static int accentParsed = DEFAULT_ACCENT;

    /** Cached on the raw string because this is read dozens of times per frame. */
    public static int accentColor() {
        if (!clientReady()) {
            return DEFAULT_ACCENT;
        }
        String raw = CLIENT.accentColor.get();
        if (!raw.equals(accentRaw)) {
            accentRaw = raw;
            try {
                accentParsed = 0xFF000000 | (Integer.parseInt(raw.replace("#", "").trim(), 16) & 0xFFFFFF);
            } catch (NumberFormatException ignored) {
                accentParsed = DEFAULT_ACCENT;
            }
        }
        return accentParsed;
    }
}
