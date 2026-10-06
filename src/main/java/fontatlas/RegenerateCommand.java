package fontatlas;

import com.google.gson.JsonObject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * Rebuilds the ENTIRE bitmap font atlas with the given font, covering ASCII,
 * fullwidth punctuation, the previous atlas charset, the characters used by
 * the scanned sources and an extra charset file (by default the bundled 3500
 * common Chinese characters). Characters the font cannot display (arrows,
 * geometric shapes, ...) are copied verbatim from the previous atlas.
 */
@Command(name = "regenerate",
        mixinStandardHelpOptions = true,
        description = "Rebuild the whole atlas sheet and descriptor from a TTF/OTF font.")
public class RegenerateCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Atlas descriptor JSON (read for size and the previous charset; rewritten).")
    private Path atlasJson;

    @Parameters(index = "1", description = "Atlas sheet PNG (read for glyphs the font cannot display; rewritten).")
    private Path atlasPng;

    @Parameters(index = "2", description = "TTF/OTF file of the font to rasterize with.")
    private String fontFile;

    @Parameters(index = "3..*", arity = "0..*", description = "Directories scanned recursively for text files.")
    private List<Path> sourceRoots = new ArrayList<>();

    @Option(names = {"-w", "--sheet-width"}, defaultValue = "1024", description = "Sheet width in pixels (default: ${DEFAULT-VALUE}).")
    private int sheetWidth;

    @Option(names = {"-c", "--charset-file"}, description = "Charset file to include (default: the bundled 3500 common Chinese characters).")
    private String charsetFile;

    @Option(names = {"-e", "--ext"}, split = ",", defaultValue = "java",
            description = "Comma-separated file extensions to scan (default: ${DEFAULT-VALUE}).")
    private List<String> extensions;

    @Option(names = "--no-common-chars", description = "Skip the bundled common-Chinese charset and any charset file.")
    private boolean noCommonChars;

    @Override
    public Integer call() throws Exception {
        JsonObject oldAtlas = Glyphs.readAtlas(atlasJson);
        int size = oldAtlas.get("size").getAsInt();
        Map<String, JsonObject> oldEntries = Glyphs.entries(oldAtlas);
        BufferedImage oldSheet = ImageIO.read(atlasPng.toFile());

        // ---- 1. build the charset ----
        TreeSet<Character> charset = new TreeSet<>();
        for (char c = 33; c <= 126; c++) charset.add(c);          // printable ASCII
        charset.add(' ');
        for (char c = 0xFF01; c <= 0xFF5E; c++) charset.add(c);   // fullwidth forms
        for (String key : oldEntries.keySet()) {                  // everything the atlas had
            if (key.length() == 1) charset.add(key.charAt(0));
        }
        SourceScanner.collect(charset, sourceRoots, extensions);  // everything the UI uses
        charset.removeIf(c -> c < 32 || c == 127);
        if (!noCommonChars) {
            Glyphs.addBundledCommonCharset(charset);
            if (charsetFile != null) Glyphs.addCharsetFile(charset, Paths.get(charsetFile));
        }
        charset.add('▯'); // replacement glyph
        System.out.println("charset size: " + charset.size());

        // ---- 2. load the font ----
        Font font;
        try (var in = Files.newInputStream(Paths.get(fontFile))) {
            Font created = Font.createFont(Font.TRUETYPE_FONT, in);
            font = created.deriveFont(Font.BOLD, (float) size);
            System.out.println("font: " + created.getFamily() + " / " + created.getName());
        }

        // ---- 3. rasterize / copy ----
        FontRenderContext frc = new FontRenderContext(null, true, true);
        List<Placed> cells = new ArrayList<>();
        int copiedFromOld = 0;
        for (char c : charset) {
            String key = String.valueOf(c);
            if (c != ' ' && !font.canDisplay(c) && oldEntries.containsKey(key)) {
                JsonObject e = oldEntries.get(key);
                BufferedImage img = oldSheet.getSubimage(e.get("x").getAsInt(), e.get("y").getAsInt(),
                        e.get("width").getAsInt(), e.get("height").getAsInt());
                cells.add(new Placed(key, new Glyphs.Glyph(img, e.get("advance").getAsInt(),
                        e.get("originY").getAsInt()), e.get("originX").getAsInt()));
                copiedFromOld++;
                continue;
            }
            cells.add(new Placed(key, Glyphs.rasterize(key, font, frc), 1));
        }
        System.out.println("glyphs: " + cells.size() + " (copied from old atlas: " + copiedFromOld + ")");

        // ---- 4. lay out & render the sheet ----
        int cursorX = 0, cursorY = 0, rowH = 0;
        for (Placed cell : cells) {
            int w = cell.glyph.image().getWidth(), h = cell.glyph.image().getHeight();
            if (cursorX + w > sheetWidth) {
                cursorX = 0;
                cursorY += rowH + 1;
                rowH = 0;
            }
            cell.x = cursorX;
            cell.y = cursorY;
            cursorX += w + 1;
            rowH = Math.max(rowH, h);
        }
        int sheetHeight = cursorY + rowH;
        System.out.println("sheet: " + sheetWidth + "x" + sheetHeight);

        BufferedImage out = new BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (Placed cell : cells) g.drawImage(cell.glyph.image(), cell.x, cell.y, null);
        g.dispose();
        ImageIO.write(out, "png", atlasPng.toFile());

        // ---- 5. write JSON ----
        Map<String, JsonObject> entries = new LinkedHashMap<>();
        for (Placed cell : cells) {
            JsonObject o = new JsonObject();
            o.addProperty("x", cell.x);
            o.addProperty("y", cell.y);
            o.addProperty("width", cell.glyph.image().getWidth());
            o.addProperty("height", cell.glyph.image().getHeight());
            o.addProperty("originX", cell.originX);
            o.addProperty("originY", cell.glyph.baselineRow());
            o.addProperty("advance", cell.glyph.advance());
            entries.put(cell.key, o);
        }
        Glyphs.writeAtlas(atlasJson, size, sheetWidth, sheetHeight, entries);

        // ---- 6. sanity checks ----
        List<String> required = new ArrayList<>(List.of("A", " ", "▯"));
        if (oldEntries.containsKey("■")) required.add("■"); // block glyph, only carried over if the atlas had it
        for (String must : required) {
            if (!entries.containsKey(must)) throw new IllegalStateException("missing required glyph: " + must);
        }
        System.out.println("OK: required glyphs present, " + entries.size() + " entries");
        return 0;
    }

    private static class Placed {
        final String key;
        final Glyphs.Glyph glyph;
        final int originX;
        int x, y;

        Placed(String key, Glyphs.Glyph glyph, int originX) {
            this.key = key;
            this.glyph = glyph;
            this.originX = originX;
        }
    }
}
