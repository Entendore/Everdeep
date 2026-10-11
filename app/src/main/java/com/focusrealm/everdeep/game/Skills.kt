package com.focusrealm.everdeep.game

import kotlin.random.Random

enum class School { BLADE, EMBER, TIDE, BLOOM, VOID }
enum class SkillKind { ACTIVE, PASSIVE }
enum class SkillFx {
    NONE, DMG_SINGLE, DMG_RANGED, DMG_ALL, HEAL,
    BUFF_ATK, BUFF_DEF, BLINK, STUN, POTION
}

class Skill(
    val id: String, val name: String, val school: School, val tier: Int,
    val kind: SkillKind,
    val fx: SkillFx = SkillFx.NONE,
    val cd: Int = 0, val power: Int = 0, val dur: Int = 0,
    val pAtk: Int = 0, val pDef: Int = 0, val pHp: Int = 0,
    val pCrit: Int = 0, val pThorns: Int = 0, val pLifesteal: Int = 0,
    val pVision: Int = 0, val pPotion: Int = 0, val pGold: Int = 0, val pXp: Int = 0,
    val desc: String = ""
)

object Skills {

    fun emoji(school: School): String = when (school) {
        School.BLADE -> "⚔️"; School.EMBER -> "🔥"; School.TIDE -> "🌊"
        School.BLOOM -> "🌿"; School.VOID -> "🌑"
    }

    private fun A(id: String, name: String, s: School, t: Int,
                  fx: SkillFx, cd: Int, pow: Int, dur: Int = 0) =
        Skill(id, name, s, t, SkillKind.ACTIVE, fx, cd, pow, dur)

    private fun P(id: String, name: String, s: School, t: Int,
                  atk: Int = 0, def: Int = 0, hp: Int = 0, crit: Int = 0,
                  thorns: Int = 0, steal: Int = 0, vision: Int = 0,
                  potion: Int = 0, gold: Int = 0, xp: Int = 0) =
        Skill(id, name, s, t, SkillKind.PASSIVE,
              pAtk = atk, pDef = def, pHp = hp, pCrit = crit, pThorns = thorns,
              pLifesteal = steal, pVision = vision, pPotion = potion,
              pGold = gold, pXp = xp)

    val BLADE = listOf(
        P("b_keen","Keen Edge",School.BLADE,0, atk=1),
        P("b_iron","Iron Skin",School.BLADE,0, def=1),
        P("b_march","Marching Drill",School.BLADE,0, hp=6),
        P("b_killer","Killer Instinct",School.BLADE,0, crit=5),
        P("b_blood","Bloodletter",School.BLADE,0, steal=1),
        A("b_cleave","Practice Cleave",School.BLADE,0, SkillFx.DMG_ALL,5,3),
        A("b_bash","Shield Bash",School.BLADE,1, SkillFx.STUN,5,3),
        A("b_warcry","War Cry",School.BLADE,1, SkillFx.BUFF_ATK,12,3,6),
        A("b_brace","Brace",School.BLADE,1, SkillFx.BUFF_DEF,12,3,6),
        A("b_wind","Second Wind",School.BLADE,1, SkillFx.HEAL,15,8),
        P("b_riposte","Riposte",School.BLADE,1, thorns=2),
        P("b_exec","Executioner",School.BLADE,1, crit=8),
        A("b_onslaught","Onslaught",School.BLADE,2, SkillFx.DMG_ALL,7,5),
        A("b_vital","Vital Strike",School.BLADE,2, SkillFx.DMG_SINGLE,8,10),
        A("b_adrenal","Adrenal Surge",School.BLADE,2, SkillFx.BUFF_ATK,14,5,5),
        A("b_bastion","Bastion",School.BLADE,2, SkillFx.BUFF_DEF,14,5,5),
        P("b_frenzy","Frenzy",School.BLADE,2, steal=2),
        P("b_colossus","Colossus Might",School.BLADE,3, atk=2, def=1),
        A("b_last","Last Stand",School.BLADE,3, SkillFx.HEAL,18,14),
        A("b_sunder","Sunder",School.BLADE,3, SkillFx.DMG_SINGLE,9,14))

    val EMBER = listOf(
        P("e_warm","Warm Hands",School.EMBER,0, potion=1),
        P("e_cinder","Cinder Heart",School.EMBER,0, hp=5),
        P("e_sparks","Sparks",School.EMBER,0, crit=4),
        P("e_eyes","Ember Eyes",School.EMBER,0, vision=1),
        P("e_gold","Ash Gold",School.EMBER,0, gold=3),
        A("e_bolt","Firebolt",School.EMBER,0, SkillFx.DMG_RANGED,4,5),
        A("e_lash","Flame Lash",School.EMBER,1, SkillFx.DMG_SINGLE,5,6),
        A("e_ring","Ring of Fire",School.EMBER,1, SkillFx.DMG_ALL,6,4),
        A("e_kindle","Kindle",School.EMBER,1, SkillFx.HEAL,14,7),
        A("e_resolve","Burning Resolve",School.EMBER,1, SkillFx.BUFF_ATK,13,4,5),
        P("e_magma","Magma Skin",School.EMBER,1, thorns=3),
        P("e_smoke","Smoke Lungs",School.EMBER,1, def=1),
        A("e_immolate","Immolate",School.EMBER,2, SkillFx.DMG_ALL,8,6),
        A("e_pyre","Pyre Blast",School.EMBER,2, SkillFx.DMG_RANGED,7,9),
        A("e_cloak","Cinder Cloak",School.EMBER,2, SkillFx.BUFF_DEF,13,4,5),
        P("e_forge","Forge Blessing",School.EMBER,2, atk=1, crit=3),
        P("e_living","Living Flame",School.EMBER,2, atk=1, steal=1),
        A("e_dragon","Dragon Cough",School.EMBER,3, SkillFx.DMG_RANGED,9,12),
        P("e_heart","Heart of the Forge",School.EMBER,3, hp=8, atk=1),
        A("e_rebirth","Ashen Rebirth",School.EMBER,3, SkillFx.HEAL,20,12))

    val TIDE = listOf(
        P("t_cool","Cool Head",School.TIDE,0, potion=1),
        P("t_salve","Salve of Brine",School.TIDE,0, hp=5),
        P("t_sense","Tide Sense",School.TIDE,0, vision=1),
        P("t_pearl","Pearl Diver",School.TIDE,0, gold=3),
        P("t_numb","Numbing Cold",School.TIDE,0, thorns=2),
        A("t_needle","Frost Needle",School.TIDE,0, SkillFx.DMG_RANGED,3,4),
        A("t_rip","Riptide",School.TIDE,1, SkillFx.DMG_ALL,6,4),
        A("t_mist","Mist Veil",School.TIDE,1, SkillFx.BUFF_DEF,12,4,6),
        A("t_spring","Healing Spring",School.TIDE,1, SkillFx.HEAL,14,8),
        A("t_undertow","Undertow Grasp",School.TIDE,1, SkillFx.STUN,10,2),
        P("t_breath","Deep Breath",School.TIDE,1, hp=7),
        P("t_siren","Siren Grace",School.TIDE,1, crit=5),
        A("t_glacial","Glacial Spike",School.TIDE,2, SkillFx.DMG_SINGLE,7,8),
        A("t_blizzard","Blizzard",School.TIDE,2, SkillFx.DMG_ALL,8,6),
        P("t_coral","Coral Skin",School.TIDE,2, def=2),
        P("t_lore","Drowned Lore",School.TIDE,2, xp=2),
        A("t_ebb","Ebb and Flow",School.TIDE,2, SkillFx.HEAL,16,11),
        A("t_leviathan","Leviathan's Gift",School.TIDE,3, SkillFx.BUFF_ATK,13,4,6),
        P("t_abyss","Abyssal Heart",School.TIDE,3, hp=10, def=1),
        A("t_maelstrom","Maelstrom",School.TIDE,3, SkillFx.DMG_ALL,10,9))

    val BLOOM = listOf(
        P("f_green","Green Thumb",School.BLOOM,0, potion=1),
        P("f_bark","Thick Bark",School.BLOOM,0, def=1),
        P("f_sap","Sun Sap",School.BLOOM,0, hp=5),
        P("f_luck","Spore Luck",School.BLOOM,0, crit=4),
        P("f_sight","Mycelial Sight",School.BLOOM,0, vision=1),
        A("f_thorns","Thornburst",School.BLOOM,0, SkillFx.DMG_ALL,5,3),
        A("f_mend","Mend Wound",School.BLOOM,1, SkillFx.HEAL,12,7),
        A("f_root","Root Grasp",School.BLOOM,1, SkillFx.STUN,9,2),
        A("f_sapstr","Sap Strength",School.BLOOM,1, SkillFx.BUFF_ATK,12,3,6),
        A("f_wall","Bark Wall",School.BLOOM,1, SkillFx.BUFF_DEF,12,4,6),
        P("f_regrow","Regrowth",School.BLOOM,1, steal=1),
        P("f_forage","Forager",School.BLOOM,1, gold=2, potion=1),
        P("f_vigor","Blooming Vigor",School.BLOOM,2, hp=8),
        P("f_grove","Grove Guard",School.BLOOM,2, def=1, thorns=1),
        A("f_great","Great Mend",School.BLOOM,2, SkillFx.HEAL,16,12),
        A("f_cloud","Spore Cloud",School.BLOOM,2, SkillFx.DMG_RANGED,6,6),
        P("f_photo","Photosynthesis",School.BLOOM,2, potion=2),
        P("f_elder","Elder Root",School.BLOOM,3, hp=6, def=1),
        P("f_world","World-Tree's Patience",School.BLOOM,3, hp=12),
        A("f_surge","Verdant Surge",School.BLOOM,3, SkillFx.HEAL,17,14))

    val VOIDL = listOf(
        P("v_eye","Third Eye",School.VOID,0, vision=1),
        P("v_scribe","Star Scribe",School.VOID,0, xp=2),
        P("v_skin","Null Skin",School.VOID,0, def=1),
        P("v_luck","Borrowed Luck",School.VOID,0, crit=5),
        P("v_purse","Void Purse",School.VOID,0, gold=4),
        A("v_bolt","Void Bolt",School.VOID,0, SkillFx.DMG_RANGED,4,5),
        A("v_crush","Graviton Crush",School.VOID,1, SkillFx.DMG_SINGLE,6,7),
        A("v_phase","Phase Step",School.VOID,1, SkillFx.BLINK,10,0),
        A("v_sigil","Warding Sigil",School.VOID,1, SkillFx.BUFF_DEF,12,4,6),
        A("v_mind","Mind Spike",School.VOID,1, SkillFx.STUN,9,2),
        P("v_touch","Entropic Touch",School.VOID,1, steal=1),
        P("v_cache","Whisper Cache",School.VOID,1, potion=1),
        A("v_star","Star Fall",School.VOID,2, SkillFx.DMG_ALL,7,5),
        P("v_chrono","Chrono Sight",School.VOID,2, crit=6),
        A("v_rift","Rift Blade",School.VOID,2, SkillFx.BUFF_ATK,12,4,5),
        P("v_deeper","Deeper Sight",School.VOID,2, vision=2),
        A("v_siphon","Soul Siphon",School.VOID,2, SkillFx.DMG_RANGED,8,8),
        A("v_potion","Quantum Potion",School.VOID,3, SkillFx.POTION,20,1),
        A("v_horizon","Event Horizon",School.VOID,3, SkillFx.DMG_ALL,10,8),
        P("v_gift","The Unnamed Gift",School.VOID,3, atk=1, def=1, hp=4, xp=1))

    val ALL: List<Skill> = BLADE + EMBER + TIDE + BLOOM + VOIDL

    fun ofTier(tier: Int): List<Skill> = ALL.filter { it.tier == tier }
    fun randomTier(tiers: List<Int>, exclude: Set<String>): Skill? {
        val pool = ALL.filter { it.tier in tiers && it.id !in exclude }
        return pool.randomOrNull()
    }
    fun startPair(): Pair<Skill, Skill> {
        val act = ALL.filter { it.tier == 0 && it.kind == SkillKind.ACTIVE }.random()
        val pas = ALL.filter { it.tier == 0 && it.kind == SkillKind.PASSIVE }.random()
        return act to pas
    }
}

object SkillForge {
    private val ONSET = listOf("vael","thil","mor","kae","syr","un","zir","ol","grav",
                               "ny","qel","ash","ith","ur","sol","nem")
    private val NUCLEUS = listOf("a","e","i","o","u","ae","y")
    private val CODA = listOf("morph","vein","mark","song","bind","wrought","grip",
                              "chord","hour","reave","call","shard")
    private val PREFIX = mapOf(
        School.BLADE to listOf("Honed","Grim","Sergeant's","Whetted","Iron"),
        School.EMBER to listOf("Cinder","Kiln","Slag","Pyric","Warm"),
        School.TIDE to listOf("Brine","Slow","Pearl","Drowned","Salt"),
        School.BLOOM to listOf("Rooted","Patient","Blooming","Green","Spored"),
        School.VOID to listOf("Hollow","Counted","Nameless","Pale","Unmoored"))

    private fun forgedName(school: School): String {
        val core = (ONSET.random() + NUCLEUS.random() + CODA.random())
            .replaceFirstChar { it.uppercase() }
        return "${PREFIX[school]!!.random()} $core"
    }

    fun forge(school: School, depth: Int): Skill {
        val pow = 4 + depth / 2 + Random.nextInt(3)
        val t = 2 + Random.nextInt(2)
        val id = "forged_${System.nanoTime()}"
        return when (Random.nextInt(100)) {
            in 0 until 35 -> Skill(id, forgedName(school), school, t, SkillKind.ACTIVE,
                SkillFx.DMG_RANGED, 6 + Random.nextInt(4), pow)
            in 35 until 55 -> Skill(id, forgedName(school), school, t, SkillKind.ACTIVE,
                SkillFx.DMG_ALL, 7 + Random.nextInt(4), (pow * 0.8f).toInt().coerceAtLeast(3))
            in 55 until 70 -> Skill(id, forgedName(school), school, t, SkillKind.ACTIVE,
                SkillFx.HEAL, 14 + Random.nextInt(5), pow + 2)
            in 70 until 82 -> Skill(id, forgedName(school), school, t, SkillKind.ACTIVE,
                SkillFx.STUN, 9 + Random.nextInt(4), pow / 2)
            in 82 until 92 -> Skill(id, forgedName(school), school, t, SkillKind.PASSIVE,
                pAtk = 1 + Random.nextInt(2), pCrit = Random.nextInt(6))
            else -> Skill(id, forgedName(school), school, t, SkillKind.PASSIVE,
                pHp = 5 + Random.nextInt(7), pThorns = 1 + Random.nextInt(3))
        }
    }
}
