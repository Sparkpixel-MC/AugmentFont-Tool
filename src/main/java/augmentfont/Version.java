package augmentfont;

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads the build version injected by Gradle into augmentfont-version.properties. */
public class Version implements IVersionProvider {

    @Override
    public String[] getVersion() throws IOException {
        try (InputStream in = Version.class.getResourceAsStream("/augmentfont-version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("version", "unknown");
                return new String[]{"augmentfont-tool " + v};
            }
        }
        return new String[]{"augmentfont-tool unknown"};
    }
}
