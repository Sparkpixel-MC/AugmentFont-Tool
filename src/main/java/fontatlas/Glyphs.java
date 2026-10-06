package fontatlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Shared glyph rasterization and atlas I/O helpers. */
final class Glyphs {

    private Glyphs() {
    }

    /** One laid-out glyph: rasterized ink plus its metrics. */
    record Glyph(BufferedImage image, int advance, int baselineRow) {
    }

    /**
     * Rasterizes {@code s} with white ink on a transparent background so the ink
     * top-left lands at (1,1); the baseline row inside the cell is returned too.
     */
    static Glyph rasterize(String s, Font font, FontRenderContext frc) {
        TextLayout tl = new TextLayout(s, font, frc);
        var pb = tl.getPixelBounds(frc, 0, 0);
        int advance = Math.round(tl.getAdvance());
        int w = Math.max(pb.width + 2, 3), h = Math.max(pb.height + 2, 3);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);
        g.setFont(font);
        g.drawString(s, 1 - pb.x, 1 - pb.y);
        g.dispose();
        return new Glyph(img, advance, 1 - pb.y);
    }

    static JsonObject readAtlas(Path json) throws IOException {
        return com.google.gson.JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    static Map<String, JsonObject> entries(JsonObject atlas) {
        Map<String, JsonObject> entries = new java.util.LinkedHashMap<>();
        atlas.getAsJsonObject("characters").entrySet()
                .forEach(e -> entries.put(e.getKey(), e.getValue().getAsJsonObject()));
        return entries;
    }

    /** Serializes the atlas back to {@code json} with stable, pretty formatting. */
    static void writeAtlas(Path json, int size, int width, int height, Map<String, JsonObject> entries)
            throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("size", size);
        root.addProperty("width", width);
        root.addProperty("height", height);
        JsonObject chars = new JsonObject();
        entries.forEach(chars::add);
        root.add("characters", chars);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.writeString(json, gson.toJson(root), StandardCharsets.UTF_8);
    }

    /** Adds every non-whitespace character of a UTF-8 charset file (one per line or inline). */
    static void addCharsetFile(Set<Character> charset, Path file) throws IOException {
        for (char c : Files.readString(file, StandardCharsets.UTF_8).toCharArray()) {
            if (!Character.isWhitespace(c)) charset.add(c);
        }
    }

    /** Adds the bundled 3500 common Chinese characters. */
    static void addBundledCommonCharset(Set<Character> charset) {
        try (var in = Glyphs.class.getResourceAsStream("/common3500.txt")) {
            if (in == null) throw new IllegalStateException("bundled common3500.txt missing from jar");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (char c : text.toCharArray()) {
                if (!Character.isWhitespace(c)) charset.add(c);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
