package com.focusrealm.everdeep.game

object Bestiary {

    private val DUST  = 0xFFA08A6A.toInt(); private val BONE  = 0xFFC9C2B0.toInt()
    private val SICK  = 0xFF9FB59A.toInt(); private val BLOOD = 0xFFC05A5A.toInt()
    private val SPEC  = 0xFF9AA3B2.toInt(); private val PURP  = 0xFF8A7AB5.toInt()
    private val MOSS  = 0xFF7FA06A.toInt(); private val PINK  = 0xFFD98FB0.toInt()
    private val MAG   = 0xFFC77BA8.toInt(); private val CREAM = 0xFFD9C9A8.toInt()
    private val ORNG  = 0xFFE2703A.toInt(); private val LAVA  = 0xFFD9542E.toInt()
    private val MAGM  = 0xFFB85C2E.toInt(); private val FIRE  = 0xFFF0A35C.toInt()
    private val TEAL  = 0xFF6FA8B8.toInt(); private val FOAM  = 0xFF66C2B0.toInt()
    private val DEEP  = 0xFF5A7EA0.toInt(); private val VIOL  = 0xFFB39DDB.toInt()
    private val STAR  = 0xFFD6CFF2.toInt(); private val VOID  = 0xFF7A6FA0.toInt()
    private val BRASS = 0xFFC9A96A.toInt(); private val SILV  = 0xFFB8C4C4.toInt()

    private fun m(id: String, glyph: Char, name: String, tier: Int,
                  hp: Int, atk: Int, def: Int, xp: Int, gold: Int, color: Int) =
        MType(id, glyph, name, hp, atk, def, xp, gold, boss = false, tier = tier, color = color)

    val CRYPT = listOf(
        m("rat",'r',"Rat",0,4,2,0,3,2,DUST),
        m("bat",'b',"Cave Bat",0,5,2,0,4,2,SPEC),
        m("goblin",'g',"Goblin",0,9,3,0,6,5,MOSS),
        m("kobold",'k',"Kobold",0,8,3,0,5,4,ORNG),
        m("serpent",'c',"Crypt Serpent",0,7,3,0,5,4,MOSS),
        m("spider",'s',"Tomb Spider",1,11,4,0,8,6,PURP),
        m("skeleton",'X',"Skeleton",1,13,4,1,9,7,BONE),
        m("archer",'a',"Bone Archer",1,10,5,0,10,8,BONE),
        m("hound",'h',"Grave Hound",1,12,4,0,9,6,DUST),
        m("ghoul",'G',"Ghoul",1,16,5,1,11,8,SICK),
        m("wight",'w',"Tomb Wight",2,18,5,1,13,10,SPEC),
        m("wraith",'v',"Wraith",2,15,7,2,16,12,SPEC),
        m("revenant",'R',"Revenant",2,22,6,1,16,12,BLOOD),
        m("plague",'p',"Plague Bearer",2,20,5,2,15,11,MOSS),
        m("lich",'L',"Lich Apprentice",2,17,8,2,18,14,VIOL),
        m("colossus",'B',"Bone Colossus",3,34,8,3,24,18,BONE),
        m("deathknight",'D',"Death Knight",3,30,9,2,26,20,SILV),
        m("necromancer",'n',"Necromancer",3,24,10,2,26,22,PURP),
        m("bonedragon",'A',"Bone Dragon",3,42,10,3,32,26,CREAM),
        m("hollowking",'K',"The Hollow King",3,38,11,3,34,30,BLOOD))

    val FUNGAL = listOf(
        m("sporeling",'m',"Sporeling",0,6,2,0,4,3,PINK),
        m("tick",'t',"Cave Tick",0,5,2,0,4,2,DUST),
        m("fungalmouse",'μ',"Fungal Mouse",0,5,2,0,4,2,SICK),
        m("mosslurker",'l',"Moss Lurker",0,8,2,0,5,3,MOSS),
        m("puffshroom",'π',"Puff Shroom",0,7,3,0,5,4,CREAM),
        m("myconid",'M',"Myconid",1,14,4,1,10,7,MAG),
        m("barkcrawler",'ç',"Bark Crawler",1,11,4,0,9,6,DUST),
        m("sporedrifter",'ρ',"Spore Drifter",1,10,5,0,10,7,PINK),
        m("thornslime",'y',"Thorn Slime",1,13,3,1,9,6,MOSS),
        m("rotbat",'β',"Rot Bat",1,9,5,0,9,6,PURP),
        m("hulk",'H',"Fungal Hulk",2,26,7,2,18,12,MOSS),
        m("sporequeen",'q',"Spore Queen",2,20,6,2,17,13,MAG),
        m("capguardian",'Φ',"Cap Guardian",2,24,6,2,18,13,CREAM),
        m("miasma",'φ',"Miasma Wisp",2,16,8,1,17,13,VIOL),
        m("roothorror",'η',"Root Horror",2,25,7,1,19,14,DUST),
        m("grovetyrant",'Ψ',"Grove Tyrant",3,36,9,2,28,22,MOSS),
        m("sporemind",'ξ',"Sporemind",3,30,10,2,28,22,PURP),
        m("eldermyconid",'Θ',"Elder Myconid",3,34,8,3,27,20,MAG),
        m("bloomwraith",'ω',"Bloom Wraith",3,28,10,2,28,21,PINK),
        m("verdantmaw",'Ç',"The Verdant Maw",3,44,10,3,33,26,SICK))

    val EMBER = listOf(
        m("imp",'i',"Cinder Imp",0,8,4,0,7,4,ORNG),
        m("fireant",'æ',"Fire Ant",0,6,3,0,5,3,LAVA),
        m("coalslime",'j',"Coal Slime",0,9,2,1,6,4,DUST),
        m("ashwisp",'à',"Ash Wisp",0,6,4,0,6,4,SILV),
        m("emberrat",'è',"Ember Rat",0,7,3,0,5,4,MAGM),
        m("magmaslug",'J',"Magma Slug",1,20,5,3,14,9,LAVA),
        m("firebeetle",'î',"Fire Beetle",1,12,4,1,10,7,BRASS),
        m("salamander",'σ',"Salamander",1,14,5,1,12,9,ORNG),
        m("lavatoad",'ò',"Lava Toad",1,16,4,1,12,8,MAGM),
        m("smokeshade",'ù',"Smoke Shade",1,12,6,0,13,9,SILV),
        m("golem",'E',"Ember Golem",2,34,8,3,24,20,BRASS),
        m("djinn",'Ξ',"Flame Djinn",2,22,9,1,22,17,FIRE),
        m("cinderspawn",'ú',"Cinderspawn",2,18,7,1,17,12,LAVA),
        m("basalt",'Â',"Basalt Brute",2,30,7,3,22,16,DUST),
        m("moltenserpent",'ς',"Molten Serpent",2,24,8,2,21,15,MAGM),
        m("magmatitan",'Σ',"Magma Titan",3,40,10,3,30,24,MAGM),
        m("pyre",'ø',"Pyre Elemental",3,32,11,2,30,24,FIRE),
        m("wyrmling",'ý',"Obsidian Wyrmling",3,36,9,3,29,23,PURP),
        m("forgeheart",'Ö',"The Forge Heart",3,46,10,3,33,27,LAVA),
        m("ashenbehemoth",'ö',"Ashen Behemoth",3,38,11,2,31,25,ORNG))

    val DROWNED = listOf(
        m("eel",'e',"Deep Eel",0,9,4,0,8,5,TEAL),
        m("brine",'á',"Brine Shrimp",0,5,2,0,4,2,FOAM),
        m("puddle",'ü',"Puddle Slime",0,8,2,1,5,4,DEEP),
        m("leech",'ū',"Cave Leech",0,7,3,0,5,4,PURP),
        m("barnacle",'¤',"Barnacle Creep",0,10,3,1,6,4,BONE),
        m("drowned",'d',"Drowned One",1,13,5,1,10,6,DEEP),
        m("siren",'S',"Siren",1,20,7,1,17,14,FOAM),
        m("saltwraith",'û',"Salt Wraith",1,15,6,1,14,10,SPEC),
        m("tidecrawler",'þ',"Tide Crawler",1,12,5,1,11,8,TEAL),
        m("angler",'ô',"Anglerfish",1,16,5,1,13,10,DEEP),
        m("riptide",'Œ',"Rip Tide Elemental",2,22,8,1,19,15,FOAM),
        m("deepogre",'Ò',"Deep Ogre",2,28,8,2,22,17,MOSS),
        m("coralgolem",'Ô',"Coral Golem",2,30,7,3,23,18,PINK),
        m("seawitch",'ő',"Sea Witch",2,20,9,2,22,18,VIOL),
        m("murk",'ű',"Murk Stalker",2,21,8,1,20,15,DEEP),
        m("leviathan",'Ë',"Leviathan Spawn",3,38,10,2,30,25,TEAL),
        m("abyssal",'ñ',"Abyssal Serpent",3,34,10,2,29,23,DEEP),
        m("drowningking",'Æ',"The Drowning King",3,40,11,3,33,27,FOAM),
        m("maelstrom",'É',"Maelstrom Elemental",3,34,11,2,31,25,SPEC),
        m("deepone",'Û',"Deep One Elder",3,36,9,3,29,23,MOSS))

    val VOIDREACH = listOf(
        m("voidwisp",'z',"Void Wisp",0,6,3,0,5,4,VIOL),
        m("starmote",'·',"Star Mote",0,4,3,0,5,4,STAR),
        m("nullmoth",'œ',"Null Moth",0,6,2,0,4,3,VOID),
        m("echoshade",'é',"Echo Shade",0,7,3,0,5,4,SPEC),
        m("dustofages",'ß',"Dust of Ages",0,5,2,1,4,3,SILV),
        m("voidhound",'h',"Void Hound",1,14,6,0,13,9,VOID),
        m("horror",'x',"Nameless Horror",1,22,8,2,20,15,PURP),
        m("quantumthief",'ð',"Quantum Thief",1,13,7,0,15,11,STAR),
        m("staringeye",'õ',"Staring Eye",1,15,6,1,15,11,BLOOD),
        m("voidslime",'ū',"Void Slime",1,16,5,1,13,9,VOID),
        m("starspawn",'ψ',"Star Spawn",2,24,8,2,22,17,STAR),
        m("gravitywraith",'γ',"Gravity Wraith",2,20,9,1,22,17,SPEC),
        m("unseeing",'θ',"The Unseeing",2,22,7,2,21,16,VOID),
        m("entropycrawler",'κ',"Entropy Crawler",2,26,8,2,23,17,PURP),
        m("dreamparasite",'Δ',"Dream Parasite",2,21,9,1,22,17,MAG),
        m("voidleviathan",'Λ',"Void Leviathan",3,40,11,2,32,26,VIOL),
        m("palesigil",'§',"The Pale Sigil",3,30,10,3,30,25,STAR),
        m("stareater",'Å',"Star Eater",3,44,11,3,34,28,VOID),
        m("howlingdark",'¶',"The Howling Dark",3,38,12,2,33,27,PURP),
        m("namelessking",'Í',"The Nameless King",3,42,12,3,36,30,BLOOD))
}
