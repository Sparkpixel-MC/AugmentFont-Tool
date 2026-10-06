package fontatlas;

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads the build version injected by Gradle into fontatlas-version.properties. */
public class Version implements IVersionProvider {

    @Override
    public String[] getVersion() throws IOException {
        try (InputStream in = Version.class.getResourceAsStream("/fontatlas-version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("version", "unknown");
                return new String[]{"font-atlas-kit " + v};
            }
        }
        return new String[]{"font-atlas-kit unknown"};
    }
}
