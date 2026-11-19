package augmentfont;

import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** Chooses the font used to rasterize missing glyphs. */
final class FontPicker {

    private FontPicker() {
    }

    /**
     * Uses the TTF/OTF from {@code fontFile} when given; otherwise walks a list
     * of common CJK sans families and keeps the first one that covers all of
     * {@code missing}, falling back to the logical SansSerif font.
     */
    static Font pick(String fontFile, List<Character> missing, int size) throws Exception {
        if (fontFile != null && !fontFile.isEmpty()) {
            try (var in = Files.newInputStream(Paths.get(fontFile))) {
                Font created = Font.createFont(Font.TRUETYPE_FONT, in);
                Font bold = created.deriveFont(Font.BOLD, (float) size);
                System.out.println("font from file: " + created.getFamily() + " (" + created.getName() + ")");
                return bold;
            }
        }

        String needed = stringOf(missing);
        Font fallback = null;
        for (String candidate : List.of("HarmonyOS Sans SC", "HarmonyOS Sans", "MiSans", "Noto Sans SC",
                "Microsoft YaHei UI", "Microsoft YaHei", "SansSerif")) {
            Font plain = new Font(candidate, Font.PLAIN, size);
            // Java silently falls back to the Dialog font for unknown families; reject those
            boolean known = plain.getFamily().equalsIgnoreCase(candidate) || candidate.equals("SansSerif");
            if (!known) continue;
            Font styled = plain.canDisplayUpTo("同步") == -1 ? plain.deriveFont(Font.BOLD, (float) size) : plain;
            if (fallback == null) {
                fallback = styled;
                System.out.println("font: " + styled.getFamily());
            }
            if (styled.canDisplayUpTo(needed) == -1) {
                System.out.println("font: " + styled.getFamily());
                return styled;
            }
        }
        if (fallback == null) throw new IllegalStateException("no usable font found on this system");
        return fallback;
    }

    private static String stringOf(List<Character> chars) {
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }
}
