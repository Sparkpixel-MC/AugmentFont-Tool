package fontatlas;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * font-atlas-kit — toolkit for maintaining a bitmap font atlas
 * (one PNG sheet + one JSON descriptor with tight ink crops per glyph).
 *
 * Atlas conventions (em is taken from the atlas "size" field):
 *   - cells are tight ink crops with 1px padding;
 *   - originX / originY are applied as draw = pen - origin, so with 1px
 *     padding: originX = 1, originY = baseline row inside the cell;
 *   - advance = rounded TextLayout advance.
 */
@Command(name = "font-atlas-kit",
        mixinStandardHelpOptions = true,
        versionProvider = Version.class,
        description = "Toolkit for building and maintaining bitmap font atlases (PNG sheet + JSON descriptor).",
        subcommands = {AugmentCommand.class, RegenerateCommand.class, ListFontsCommand.class})
public class Main implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        if (exitCode != 0) System.exit(exitCode);
    }
}
