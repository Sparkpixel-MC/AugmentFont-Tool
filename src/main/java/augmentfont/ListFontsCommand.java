package augmentfont;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.GraphicsEnvironment;
import java.util.Locale;
import java.util.concurrent.Callable;

/** Lists installed font families relevant for CJK bitmap rendering. */
@Command(name = "list-fonts",
        mixinStandardHelpOptions = true,
        description = "List installed font families that are candidates for CJK atlas rasterization.")
public class ListFontsCommand implements Callable<Integer> {

    @Option(names = "--all", description = "List every installed family instead of CJK-relevant ones.")
    private boolean all;

    @Override
    public Integer call() {
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (all || isRelevant(family)) {
                System.out.println(family);
            }
        }
        return 0;
    }

    private static boolean isRelevant(String family) {
        String l = family.toLowerCase(Locale.ROOT);
        return l.contains("harmony") || l.contains("misans") || l.contains("yahei") || l.contains("noto sans cjk")
                || l.contains("source han") || l.contains("pingfang") || l.contains("simhei") || l.contains("dengxian")
                || l.contains("sans");
    }
}
