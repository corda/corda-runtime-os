package net.corda.schema.configuration;

/**
 * Default configuration values for associated {@link ConfigKeys}
 * <p>
 * NOTE: this is a temporary place for them.
 * Decision to be made on how to specify default values.
 * For example.
 * <ul>
 * <li>Constants</li>
 * <li>HCON config to use as fallback</li>
 * <li>JSON Schema</li>
 * </ul>
 **/
public final class ConfigDefaults {
    private ConfigDefaults() {
    }

    public static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "/corda/workspace";
    public static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/corda/tmp";
}
