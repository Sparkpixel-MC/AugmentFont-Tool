package augmentfont;

import com.google.gson.JsonObject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * Scans source files for non-ASCII characters, rasterizes the ones missing
 * from the bitmap font atlas with a matching system font, and appends them
 * below the existing sheet.
 */
@Command(name = "augment",
        mixinStandardHelpOptions = true,
        description = "Append glyphs for characters used in source files but missing from the atlas.")
public class AugmentCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Atlas descriptor JSON (read for size/width/height and existing entries).")
    private Path atlasJson;

    @Parameters(index = "1", description = "Atlas sheet PNG; extended in place.")
    private Path atlasPng;

    @Parameters(index = "2..*", arity = "0..*", description = "Directories scanned recursively for text files.")
    private List<Path> sourceRoots = new ArrayList<>();

    @Option(names = {"-f", "--font"}, description = "TTF/OTF file of the atlas font. When omitted, a matching system CJK sans is picked automatically.")
    private String fontFile;

    @Option(names = {"-e", "--ext"}, split = ",", defaultValue = "java",
            description = "Comma-separated file extensions to scan (default: ${DEFAULT-VALUE}).")
    private List<String> extensions;

    @Option(names = "--all-chars", description = "Collect all characters, not only non-ASCII ones.")
    private boolean allChars;

    @Override
    public Integer call() throws Exception {
        JsonObject atlas = Glyphs.readAtlas(atlasJson);
        int size = atlas.get("size").getAsInt();
        int sheetW = atlas.get("width").getAsInt();
        int sheetH = atlas.get("height").getAsInt();
        Map<String, JsonObject> entries = Glyphs.entries(atlas);

        // 1. collect the needed characters from the sources
        TreeSet<Character> needed = new TreeSet<>();
        SourceScanner.collect(needed, sourceRoots, extensions);
        if (!allChars) needed.removeIf(c -> c <= 127);
        List<Character> missing = new ArrayList<>();
        for (char c : needed) {
            if (!entries.containsKey(String.valueOf(c))) missing.add(c);
        }
        System.out.println("needed: " + needed.size() + ", missing from atlas: " + missing.size());
        StringBuilder sb = new StringBuilder();
        missing.forEach(sb::append);
        System.out.println("missing: " + sb);
        if (missing.isEmpty()) {
            System.out.println("nothing to do");
            return 0;
        }

        // 2. pick a matching CJK sans: prefer the exact atlas font loaded from its
        // file (per-user installed fonts often don't show up in GraphicsEnvironment)
        Font font = FontPicker.pick(fontFile, missing, size);
        if (font.canDisplayUpTo(sb.toString()) != -1) {
            System.out.println("WARNING: chosen font cannot cover all missing chars; partial coverage");
        }

        // 3. rasterize + lay out rows below the current sheet
        FontRenderContext frc = new FontRenderContext(null, true, true);
        List<Placed> cells = new ArrayList<>();
        for (char c : missing) {
            Glyphs.Glyph glyph = Glyphs.rasterize(String.valueOf(c), font, frc);
            cells.add(new Placed(String.valueOf(c), glyph));
        }

        int cursorX = 0, cursorY = sheetH, rowH = 0;
        for (Placed cell : cells) {
            if (cursorX + cell.glyph.image().getWidth() > sheetW) {
                cursorX = 0;
                cursorY += rowH;
                rowH = 0;
            }
            cell.x = cursorX;
            cell.y = cursorY;
            cursorX += cell.glyph.image().getWidth() + 1;
            rowH = Math.max(rowH, cell.glyph.image().getHeight());
        }
        int newSheetH = cursorY + rowH;

        // 4. extend PNG
        BufferedImage old = ImageIO.read(atlasPng.toFile());
        BufferedImage out = new BufferedImage(sheetW, newSheetH, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(old, 0, 0, null);
        for (Placed cell : cells) g.drawImage(cell.glyph.image(), cell.x, cell.y, null);
        g.dispose();
        ImageIO.write(out, "png", atlasPng.toFile());

        // 5. update JSON
        for (Placed cell : cells) {
            JsonObject o = new JsonObject();
            o.addProperty("x", cell.x);
            o.addProperty("y", cell.y);
            o.addProperty("width", cell.glyph.image().getWidth());
            o.addProperty("height", cell.glyph.image().getHeight());
            o.addProperty("originX", 1);
            o.addProperty("originY", cell.glyph.baselineRow());
            o.addProperty("advance", cell.glyph.advance());
            entries.put(cell.key, o);
        }
        Glyphs.writeAtlas(atlasJson, size, sheetW, newSheetH, entries);

        System.out.println("sheet: " + sheetW + "x" + sheetH + " -> " + sheetW + "x" + newSheetH);
        System.out.println("added " + cells.size() + " glyphs");
        return 0;
    }

    private static class Placed {
        final String key;
        final Glyphs.Glyph glyph;
        int x, y;

        Placed(String key, Glyphs.Glyph glyph) {
            this.key = key;
            this.glyph = glyph;
        }
    }
}
