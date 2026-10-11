package com.focusrealm.everdeep.game

import kotlin.random.Random

enum class ItemSlot { WEAPON, ARMOR, POTION, TRINKET, RELIC, GOLD }

enum class Rarity(val tag: String, val color: Int) {
    COMMON("Common", 0xFFD9C9A8.toInt()),
    UNCOMMON("Uncommon", 0xFF8FA876.toInt()),
    RARE("Rare", 0xFF7FB4C9.toInt()),
    EPIC("Epic", 0xFFB39DDB.toInt()),
    LEGENDARY("Legendary", 0xFFE0B84F.toInt())
}

class Item(
    val name: String,
    val slot: ItemSlot,
    val glyph: Char,
    val rarity: Rarity,
    val atk: Int = 0, val def: Int = 0, val hp: Int = 0,
    val heal: Int = 0,
    val potions: Int = 0, val potionPower: Int = 0, val antidote: Boolean = false,
    val crit: Int = 0, val thorns: Int = 0, val lifesteal: Int = 0,
    val vision: Int = 0, val gold: Int = 0, val xp: Int = 0,
    val price: Int = 0,
    val lore: String = ""
) {
    val equipScore: Int
        get() = atk * 3 + def * 2 + hp / 2 + crit + thorns * 2 +
                lifesteal * 3 + vision * 2 + gold + xp * 2 + potionPower * 2

    fun passiveLine(): String {
        val parts = ArrayList<String>()
        if (atk > 0) parts.add("+$atk atk")
        if (def > 0) parts.add("+$def def")
        if (hp > 0) parts.add("+$hp hp")
        if (heal > 0) parts.add("+$heal heal")
        if (potions > 0) parts.add("+$potions potions")
        if (potionPower > 0) parts.add("+$potionPower potency")
        if (crit > 0) parts.add("+$crit% crit")
        if (thorns > 0) parts.add("+$thorns thorns")
        if (lifesteal > 0) parts.add("+$lifesteal leech")
        if (vision > 0) parts.add("+$vision sight")
        if (gold > 0) parts.add("+$gold gold-find")
        if (xp > 0) parts.add("+$xp xp")
        return parts.joinToString(" · ").ifEmpty { "no measurable virtue" }
    }
}

object Items {

    val GOLD = Item("gold", ItemSlot.GOLD, '$', Rarity.COMMON)

    private fun w(n: String, r: Rarity, atk: Int, crit: Int = 0, ls: Int = 0,
                  def: Int = 0, th: Int = 0, price: Int) =
        Item(n, ItemSlot.WEAPON, '/', r, atk = atk, crit = crit, lifesteal = ls,
             def = def, thorns = th, price = price)
    private fun a(n: String, r: Rarity, def: Int, hp: Int = 0, th: Int = 0,
                  ls: Int = 0, crit: Int = 0, price: Int) =
        Item(n, ItemSlot.ARMOR, ']', r, def = def, hp = hp, thorns = th,
             lifesteal = ls, crit = crit, price = price)
    private fun p(n: String, r: Rarity, heal: Int, pot: Int = 0, hp: Int = 0,
                  ant: Boolean = false, price: Int) =
        Item(n, ItemSlot.POTION, '!', r, heal = heal, potions = 1, potionPower = pot,
             hp = hp, antidote = ant, price = price)
    private fun t(n: String, r: Rarity, price: Int, crit: Int = 0, th: Int = 0,
                  ls: Int = 0, vis: Int = 0, gold: Int = 0, xp: Int = 0,
                  hp: Int = 0, def: Int = 0) =
        Item(n, ItemSlot.TRINKET, '=', r, crit = crit, thorns = th, lifesteal = ls,
             vision = vis, gold = gold, xp = xp, hp = hp, def = def, price = price)
    private fun rl(n: String, r: Rarity, price: Int, atk: Int = 0, def: Int = 0,
                   hp: Int = 0, crit: Int = 0, th: Int = 0, ls: Int = 0,
                   vis: Int = 0, gold: Int = 0, xp: Int = 0, lore: String = "") =
        Item(n, ItemSlot.RELIC, '*', r, atk = atk, def = def, hp = hp, crit = crit,
             thorns = th, lifesteal = ls, vision = vis, gold = gold, xp = xp,
             price = price, lore = lore)

    val WEAPONS = listOf(
        w("Rusty Shiv", Rarity.COMMON, 1, price = 6),
        w("Chipped Falchion", Rarity.COMMON, 2, price = 8),
        w("Bone Shiv", Rarity.COMMON, 1, crit = 3, price = 7),
        w("Miner's Pick", Rarity.COMMON, 2, price = 8),
        w("Torch Spear", Rarity.COMMON, 2, price = 9),
        w("Grave Trench Knife", Rarity.COMMON, 1, ls = 1, price = 9),
        w("Spore-Cleaver", Rarity.UNCOMMON, 2, crit = 4, price = 12),
        w("Guard's Saber", Rarity.UNCOMMON, 3, price = 14),
        w("Bearded Axe", Rarity.UNCOMMON, 3, price = 14),
        w("Kite Spear", Rarity.UNCOMMON, 2, def = 1, price = 13),
        w("Fang Dirk", Rarity.UNCOMMON, 2, crit = 6, price = 13),
        w("Ember Brand", Rarity.UNCOMMON, 3, price = 15),
        w("Salt-Cutlass", Rarity.UNCOMMON, 2, crit = 3, price = 13),
        w("Warden's Edge", Rarity.RARE, 4, price = 20),
        w("Hollow-Iron Blade", Rarity.RARE, 3, ls = 1, price = 21),
        w("Runed Longsword", Rarity.RARE, 4, crit = 4, price = 24),
        w("Weeping Spear", Rarity.RARE, 3, ls = 2, price = 23),
        w("Starsteel Saber", Rarity.RARE, 4, crit = 3, price = 25),
        w("Tidecutter", Rarity.RARE, 3, def = 1, price = 22),
        w("Cryptlord's Rivener", Rarity.EPIC, 5, price = 36),
        w("Molten Cleaver", Rarity.EPIC, 5, crit = 5, price = 40),
        w("Thornweave Glaive", Rarity.EPIC, 4, th = 2, price = 34),
        w("Vampiric Kilij", Rarity.EPIC, 4, ls = 3, price = 42),
        w("Voidcarved Falx", Rarity.EPIC, 5, crit = 4, price = 41),
        w("Dawnbreaker, First Light", Rarity.LEGENDARY, 7, crit = 8, price = 78,
          lore = "It was forged for a morning that never came."),
        w("The Last Argument", Rarity.LEGENDARY, 8, price = 85,
          lore = "Every debate down here ends the same way."),
        w("Maithrenne, Song of Ending", Rarity.LEGENDARY, 6, ls = 3, crit = 6, price = 80,
          lore = "The last note is a verdict."),
        w("Deepgrief", Rarity.LEGENDARY, 6, def = 2, th = 3, price = 76,
          lore = "It remembers everyone it failed to save."))

    val ARMORS = listOf(
        a("Padded Jack", Rarity.COMMON, 1, price = 6),
        a("Tattered Cloak", Rarity.COMMON, 1, price = 6),
        a("Bone-Greaves", Rarity.COMMON, 1, hp = 3, price = 8),
        a("Miner's Coat", Rarity.COMMON, 1, hp = 2, price = 7),
        a("Studded Leathers", Rarity.UNCOMMON, 2, price = 12),
        a("Cryptguard's Mail", Rarity.UNCOMMON, 2, hp = 4, price = 15),
        a("Mycel Weave", Rarity.UNCOMMON, 1, hp = 6, price = 13),
        a("Slag-Hide Jerkin", Rarity.UNCOMMON, 2, th = 1, price = 15),
        a("Brinecoat", Rarity.UNCOMMON, 1, hp = 5, price = 13),
        a("Mirrored Vest", Rarity.RARE, 3, price = 20),
        a("Warden's Aegis", Rarity.RARE, 3, hp = 5, price = 24),
        a("Runed Brigandine", Rarity.RARE, 2, hp = 8, price = 23),
        a("Deepplate", Rarity.RARE, 4, price = 26),
        a("Spore-Woven Panoply", Rarity.RARE, 2, th = 2, hp = 4, price = 25),
        a("Sablemaine Harness", Rarity.EPIC, 4, hp = 6, price = 38),
        a("Starfall Cuirass", Rarity.EPIC, 3, hp = 10, price = 40),
        a("Gravity Weave", Rarity.EPIC, 3, hp = 6, th = 3, price = 42),
        a("Sunken King's Hauberk", Rarity.EPIC, 5, price = 44),
        a("Sanctified Shroud", Rarity.EPIC, 2, hp = 8, ls = 1, price = 37),
        a("Aegis of the First Vigil", Rarity.LEGENDARY, 6, hp = 8, price = 80,
          lore = "Its last bearer never learned what 'enough' meant."),
        a("Heartstone Mail", Rarity.LEGENDARY, 4, hp = 16, price = 76,
          lore = "Something in it still beats. Slowly."),
        a("The Unbroken Hour", Rarity.LEGENDARY, 5, hp = 10, th = 3, price = 82,
          lore = "One watch, held forever."),
        a("Drowned Monarch's Shell", Rarity.LEGENDARY, 5, hp = 8, th = 2, price = 78,
          lore = "Salvaged from a throne room with no doors."),
        a("Shadow-Quenched Robes", Rarity.LEGENDARY, 3, hp = 8, crit = 6, price = 74,
          lore = "Darkness, tailored."))

    val POTIONS = listOf(
        p("Cloudy Draught", Rarity.COMMON, 8, price = 6),
        p("Rough Tonic", Rarity.COMMON, 7, price = 5),
        p("Soldier's Draft", Rarity.COMMON, 10, price = 8),
        p("Hewn Root Brew", Rarity.COMMON, 9, price = 7),
        p("Red Cordial", Rarity.COMMON, 8, price = 6),
        p("Bruise-Salve", Rarity.COMMON, 8, price = 6),
        p("Gravemoss Poultice", Rarity.UNCOMMON, 12, price = 12),
        p("Silverleaf Distillate", Rarity.UNCOMMON, 14, price = 14),
        p("Ember Tonic", Rarity.UNCOMMON, 12, pot = 1, price = 15),
        p("Marrow Broth", Rarity.UNCOMMON, 10, hp = 3, price = 15),
        p("Deepwater Draught", Rarity.UNCOMMON, 13, price = 13),
        p("Sporeband Elixir", Rarity.RARE, 16, price = 20),
        p("Chalybeate Wine", Rarity.RARE, 15, pot = 1, price = 22),
        p("Warden's Merciful Vial", Rarity.RARE, 20, price = 24),
        p("Phoenix Tears", Rarity.RARE, 18, price = 26),
        p("Void-Salted Flask", Rarity.EPIC, 22, price = 34),
        p("Elixir of the Second Heart", Rarity.EPIC, 15, hp = 6, price = 38),
        p("Panacea of the Pale Garden", Rarity.EPIC, 20, ant = true, price = 36),
        p("Godsblood, Decanted", Rarity.LEGENDARY, 35, pot = 2, price = 70,
          lore = "Nobody asks whose blood."),
        p("The Unfinal Draught", Rarity.LEGENDARY, 30, hp = 8, price = 72,
          lore = "Bottled refusals, aged twenty years."))

    val TRINKETS = listOf(
        t("Carved Bone Charm", Rarity.COMMON, 6, crit = 2),
        t("Lucky Cork", Rarity.COMMON, 6, gold = 2),
        t("Tin Reliquary", Rarity.COMMON, 7, hp = 3),
        t("Tallow Candle Stub", Rarity.COMMON, 6, vis = 1),
        t("Coin of Two Faces", Rarity.UNCOMMON, 12, crit = 4, gold = 2),
        t("Bramble Signet", Rarity.UNCOMMON, 12, th = 2),
        t("Mothwing Amulet", Rarity.UNCOMMON, 13, vis = 1, hp = 3),
        t("Ledger Tooth", Rarity.UNCOMMON, 12, xp = 2),
        t("Salt-Iron Ring", Rarity.UNCOMMON, 12, def = 1, gold = 2),
        t("Lantern of the Ninth Vigil", Rarity.RARE, 22, vis = 2),
        t("Leech Ring", Rarity.RARE, 22, ls = 2),
        t("Gambler's Knucklebone", Rarity.RARE, 24, crit = 6, gold = 3),
        t("Echo-Stone", Rarity.RARE, 21, xp = 3),
        t("Sanguine Sigil", Rarity.EPIC, 36, ls = 3, crit = 4),
        t("Chromatic Prism", Rarity.EPIC, 34, vis = 2, crit = 4),
        t("Crown of Small Hours", Rarity.EPIC, 40, hp = 8, xp = 3, gold = 3),
        t("The Patient Scale", Rarity.LEGENDARY, 74, def = 2, hp = 10, th = 4, ls = 2,
          lore = "It weighs intentions, and finds most wanting."),
        t("Voidmost Eye", Rarity.LEGENDARY, 78, vis = 3, crit = 8, xp = 4,
          lore = "It looks back. Politely."))

    val RELICS = listOf(
        rl("Ember of the First Forge", Rarity.RARE, 20, atk = 2, hp = 5),
        rl("Tooth of the Verdant Maw", Rarity.RARE, 22, atk = 1, th = 3),
        rl("Pearl of the Drowned Choir", Rarity.RARE, 21, def = 2, hp = 5),
        rl("Shard of the Null Gate", Rarity.EPIC, 34, crit = 5, vis = 1),
        rl("Coin from a Dead Kingdom", Rarity.RARE, 20, gold = 6, xp = 2),
        rl("Heart of the Hollow King", Rarity.EPIC, 40, hp = 12, atk = 1),
        rl("Whisper in Amber", Rarity.RARE, 22, xp = 5),
        rl("The Warden's Oath", Rarity.EPIC, 36, def = 3, th = 2),
        rl("Star That Fell Uphill", Rarity.EPIC, 38, crit = 4, hp = 6, vis = 1),
        rl("Unfinished Crown", Rarity.LEGENDARY, 72, atk = 2, def = 2, hp = 6,
          lore = "One jewel short. The deep knows which."))

    val ALL: List<Item> = WEAPONS + ARMORS + POTIONS + TRINKETS + RELICS

    fun ofSlot(slot: ItemSlot, r: Rarity): List<Item> = when (slot) {
        ItemSlot.WEAPON -> WEAPONS
        ItemSlot.ARMOR -> ARMORS
        ItemSlot.POTION -> POTIONS
        ItemSlot.TRINKET -> TRINKETS
        ItemSlot.RELIC -> RELICS
        else -> emptyList()
    }.filter { it.rarity == r }

    private fun rollRarity(depth: Int, luck: Int = 0): Rarity {
        val d = depth + luck * 2
        val r = Random.nextInt(100)
        return when {
            r < (2 + d / 5).coerceAtMost(8) -> Rarity.LEGENDARY
            r < 8 + d / 2  -> Rarity.EPIC
            r < 20 + d     -> Rarity.RARE
            r < 45 + d / 2 -> Rarity.UNCOMMON
            else -> Rarity.COMMON
        }
    }

    fun potionRoll(depth: Int, luck: Int = 0): Item {
        val rarity = rollRarity(depth, luck)
        return POTIONS.filter { it.rarity == rarity }.ifEmpty { POTIONS }.random()
    }

    fun randomOf(depth: Int, luck: Int = 0): Item {
        if (Random.nextInt(100) < 5) return RELICS.random()
        val slot = listOf(ItemSlot.POTION, ItemSlot.WEAPON, ItemSlot.WEAPON,
                          ItemSlot.ARMOR, ItemSlot.ARMOR,
                          ItemSlot.TRINKET, ItemSlot.TRINKET).random()
        val pool = ofSlot(slot, rollRarity(depth, luck)).ifEmpty { ofSlot(slot, Rarity.COMMON) }
        var item = pool.random()
        if (item.rarity != Rarity.LEGENDARY && Random.nextInt(100) < 30)
            item = ItemForge.forge(item, depth)
        return item
    }

    fun wardenDrop(depth: Int, luck: Int = 0): Item {
        val d = depth + luck
        val r = Random.nextInt(100)
        val rarity = when {
            r < 8 + d / 3 -> Rarity.LEGENDARY
            r < 30 -> Rarity.EPIC
            else -> Rarity.RARE
        }
        val slot = listOf(ItemSlot.WEAPON, ItemSlot.ARMOR, ItemSlot.TRINKET).random()
        val pool = ofSlot(slot, rarity).ifEmpty { ofSlot(slot, Rarity.EPIC) }
                                      .ifEmpty { ofSlot(slot, Rarity.RARE) }
        var item = pool.random()
        if (item.rarity != Rarity.LEGENDARY && Random.nextInt(100) < 40)
            item = ItemForge.forge(item, depth)
        return item
    }
}

object ItemForge {
    private data class Affix(val name: String, val potionOnly: Boolean = false,
                             val atk: Int = 0, val def: Int = 0, val hp: Int = 0,
                             val heal: Int = 0, val crit: Int = 0, val th: Int = 0,
                             val ls: Int = 0, val vis: Int = 0, val gold: Int = 0,
                             val xp: Int = 0, val pot: Int = 0)

    private val PREFIXES = listOf(
        Affix("Honed", atk = 1), Affix("Bloodsoaked", ls = 1),
        Affix("Warded", def = 1), Affix("Glimmering", crit = 3),
        Affix("Weighted", hp = 4), Affix("Far-Seeing", vis = 1),
        Affix("Gilded", gold = 3), Affix("Storied", xp = 2),
        Affix("Quench-Hardened", def = 1, hp = 2), Affix("Spore-Bitten", th = 1),
        Affix("Sun-Tempered", atk = 1, hp = 1), Affix("Void-Touched", crit = 2, xp = 1),
        Affix("Tide-Blessed", hp = 2, ls = 1), Affix("Deep-Sharp", atk = 1, crit = 1))
    private val SUFFIXES = listOf(
        Affix("of the Deep", hp = 3), Affix("of Hollow Kings", atk = 1),
        Affix("of the Ninth Vigil", vis = 1), Affix("of Quiet Hours", xp = 2),
        Affix("of the Long Purse", gold = 3), Affix("of Returning", ls = 1),
        Affix("of Small Mercies", heal = 5, potionOnly = true),
        Affix("of Embers", pot = 1, potionOnly = true),
        Affix("of the Carol", crit = 2), Affix("of Roots", th = 2))

    private fun bump(i: Item, x: Affix): Item = Item(i.name, i.slot, i.glyph, i.rarity,
        i.atk + x.atk, i.def + x.def, i.hp + x.hp, i.heal + x.heal, i.potions,
        i.potionPower + x.pot, i.antidote, i.crit + x.crit, i.thorns + x.th,
        i.lifesteal + x.ls, i.vision + x.vis, i.gold + x.gold, i.xp + x.xp, i.price, i.lore)

    fun forge(base: Item, depth: Int): Item {
        val pre = PREFIXES.random()
        var out = bump(base, pre)
        var name = "${pre.name} ${base.name}"
        if (Random.nextInt(100) < 40) {
            val suf = SUFFIXES.filter { !it.potionOnly || base.slot == ItemSlot.POTION }.random()
            out = bump(out, suf)
            name += " ${suf.name}"
        }
        val rarity = Rarity.entries[base.rarity.ordinal + 1]
        return Item(name, base.slot, base.glyph, rarity,
            out.atk, out.def, out.hp, out.heal, out.potions, out.potionPower, out.antidote,
            out.crit, out.thorns, out.lifesteal, out.vision, out.gold, out.xp,
            (base.price * 1.7f).toInt() + depth, base.lore)
    }
}
