# Everdeep — a roguelike that plays only while you work

An AI hero delves an endless ASCII dungeon **while you stay off your
blocklist**. Open a blocked app — or, with the site filter armed, a blocked
domain — and she freezes mid-dungeon while rivals keep delving. Deaths build
a lineage that *learns*: brains breed through a genetic pool, strategic
choices are valued by a Q-table, tactics are searched with MCTS, and the
bestiary remembers what nearly killed your predecessors.

## Run order (generator)
`everdeep_part1.sh` → `part2` → `part3a` → `part3b` → `part3c` →
`part4b` → `part4d` → `part5a` → `part5b` → `part5c` → `part5d`
(parts 4 and 4c are superseded by 4b/4d — 4d overwrites the engine cleanly.)

Then open `Everdeep/` in Android Studio (generates the Gradle wrapper), or:
```bash
gradle wrapper && ./gradlew assembleDebug
