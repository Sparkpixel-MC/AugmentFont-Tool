package augmentfont;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recursively scans source trees for the characters they use. */
final class SourceScanner {

    private SourceScanner() {
    }

    /**
     * Adds every character occurring in files under {@code roots} whose extension
     * is in {@code extensions}. Files that are not valid UTF-8 are skipped with a
     * warning instead of aborting the run.
     */
    static void collect(Set<Character> out, List<Path> roots, List<String> extensions) throws IOException {
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                System.out.println("WARN: not a directory, skipping: " + root);
                continue;
            }
            try (var walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)
                        .filter(f -> hasExtension(f, extensions))::iterator) {
                    try {
                        for (char c : Files.readString(p, StandardCharsets.UTF_8).toCharArray()) {
                            out.add(c);
                        }
                    } catch (Exception e) {
                        System.out.println("WARN: skipping " + p + " (" + e + ")");
                    }
                }
            }
        }
    }

    private static boolean hasExtension(Path p, List<String> extensions) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extensions.contains(ext);
    }
}
