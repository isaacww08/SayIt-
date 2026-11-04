# Say It (Fabric, Minecraft 1.21.11)

Every time you **pick up** or **drop** an item you haven't unlocked yet, you get **15 seconds** to say its name out loud
in the language you picked. Say it = the item is **unlocked** (for that world + language) and you never have to say it again.
Don't = you lose the item
(pickup: that many removed from your inventory; drop: the dropped item vanishes from the world).

## Playing
1. Build the jar (JDK 21). Easiest: generate a blank 1.21.11 project at https://fabricmc.net/develop/template,
   then copy this project's `src/`, `build.gradle` and `gradle.properties` over it (that gives you the right Gradle wrapper).
   `./gradlew build` -> `build/libs/sayit-1.0.0.jar`.
2. Put the jar + Fabric API in `mods/`, launch, join a world.
3. The menu opens on first join (or press **K** any time): pick a language, done.
   The first time you pick a language the mod asks before downloading its small speech model (~30-50 MB).
   Nothing is downloaded unless you click "Download" on the confirmation screen.
   Word lists come straight from Minecraft's own language files, nothing to generate.

Menu also has: challenges on/off, show word / hard mode (English item name only).

## Notes
- Needs internet once per language, only if you agree to the download (alphacephei.com), and a working mic.
- The mod never updates itself and makes no other network requests.
- Removing items works in singleplayer / LAN host only.
- Drops are detected from the drop key (Q) with the item in your hand, not from dragging out of GUIs.
- Languages: German, Spanish, French, Italian, Portuguese (BR), Dutch, Polish, Turkish, Russian, Hindi.
  Russian and Hindi are shown (and matched) in Latin letters, e.g. Yabloko, seb.
- Optional word overrides: `<minecraft dir>/sayit/words/<code>.json` ({"item_id": "word"}).
- Tweak `CHALLENGE_SECONDS` / `MATCH_THRESHOLD` in SayIt.java.
