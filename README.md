# font-atlas-kit

A small command-line toolkit for building and maintaining **bitmap font atlases** — one PNG sheet plus one JSON descriptor with a tightly-cropped cell per glyph.

It was built to maintain the GUI bitmap font of a Java application (32px em, white ink on transparent background) but works for any project using the same atlas layout.

## Atlas format

```json
{
  "size": 32,
  "width": 512,
  "height": 8384,
  "characters": {
    "A": { "x": 0, "y": 0, "width": 21, "height": 23,
           "originX": 1, "originY": 19, "advance": 12 }
  }
}
```

Conventions:

- every cell is a **tight ink crop** with 1px padding on all sides;
- `originX` / `originY` are applied by the renderer as `draw = pen - origin`, so with 1px padding `originX = 1` and `originY` is the baseline row inside the cell;
- `advance` is the rounded `TextLayout` advance of the glyph;
- glyphs are rasterized in **white** — tint at render time.

## Commands

### `regenerate` — rebuild the whole atlas

```text
font-atlas-kit regenerate [OPTIONS] ATLAS_JSON ATLAS_PNG FONT_FILE [SOURCE_DIR]...
```

Rasters a brand-new sheet from the given TTF/OTF, covering:

- printable ASCII and fullwidth punctuation (U+FF01–U+FF5E);
- every character already present in the previous atlas;
- every character found in the scanned source directories;
- the **bundled 3500 common Chinese characters** (override with `--charset-file`, disable with `--no-common-chars`);
- `▯` as the replacement glyph.

Characters the font cannot display (arrows, geometric shapes, ...) are copied verbatim from the previous atlas so hand-made or exotic glyphs survive a rebuild.

| Option | Description |
| --- | --- |
| `-w, --sheet-width <px>` | Sheet width (default `1024`) |
| `-c, --charset-file <file>` | Extra charset file (default: bundled common-3500) |
| `--no-common-chars` | Skip the bundled / extra charset entirely |
| `-e, --ext <ext,...>` | File extensions to scan (default `java`) |

### `augment` — append missing glyphs

```text
font-atlas-kit augment [OPTIONS] ATLAS_JSON ATLAS_PNG [SOURCE_DIR]...
```

Scans the source directories for characters, picks glyphs that are **missing** from the atlas, rasterizes them with a matching CJK sans and appends them in new rows below the existing sheet. The PNG is extended in place, the JSON is rewritten. Exits cleanly when nothing is missing.

| Option | Description |
| --- | --- |
| `-f, --font <file>` | TTF/OTF of the atlas font (recommended). When omitted, a matching system CJK sans is auto-detected |
| `-e, --ext <ext,...>` | File extensions to scan (default `java`) |
| `--all-chars` | Collect all characters, not only non-ASCII ones |

### `list-fonts`

```text
font-atlas-kit list-fonts [--all]
```

Lists installed font families that are candidates for CJK rasterization (`--all` for everything).

## Building

Requires JDK 17+ (JDK 25 recommended) and the bundled Gradle wrapper:

```bash
./gradlew build          # produces build/libs/font-atlas-kit-<version>-all.jar
java -jar build/libs/font-atlas-kit-1.0.0-all.jar --help
```

## Typical workflow

```bash
# 1. one-time full rebuild with the project font
java -jar font-atlas-kit-all.jar regenerate atlas.json atlas.png HarmonyOS_Sans_SC_Bold.ttf -w 1024 src/

# 2. after new UI code lands, append whatever is missing
java -jar font-atlas-kit-all.jar augment atlas.json atlas.png -f HarmonyOS_Sans_SC_Bold.ttf src/
```

> Heads-up: rasterization happens with the Java2D stack of the *running* JVM, so glyph shapes and antialiasing depend on the JVM/platform. Rebuild on one machine and commit the result, rather than mixing machines.

## CI

`.github/workflows/build.yml` builds the fat jar on every push/PR and publishes it as a GitHub Release tagged `v<version>` (version comes from `build.gradle`) on pushes to `main`/`master`.
