package net.corda.cordapp;

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.StopExecutionException;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * Gradle plugin to ensure that the CPK and CPB Gradle plugins are correctly configured.
 */
@SuppressWarnings("unused")
public final class ConfigurationPlugin implements Plugin<Project> {
    private static final String MINIMUM_PLUGIN_VERSION_PROPERTY = "Minimum-Corda-Plugins-Version";
    private static final String PLUGIN_VERSION = "META-INF/pluginVersion.properties";
    private static final String CONFIGURATION = "cordapp-configuration.properties";

    // These are the IDs of the Corda Gradle plugins that CorDapp developers need.
    private static final List<String> CORDAPP_PLUGIN_IDS = unmodifiableList(asList(
        "net.corda.plugins.cordapp-cpk2",
        "net.corda.plugins.cordapp-cpb2",
        "net.corda.plugins.quasar-utils"
    ));

    private static final Version MINIMUM_PLUGIN_VERSION;
    static {
        URL configuration = ConfigurationPlugin.class.getResource(CONFIGURATION);
        if (configuration == null) {
            throw new InvalidUserCodeException(CONFIGURATION + " is missing.");
        }
        Properties properties = loadProperties(configuration);
        MINIMUM_PLUGIN_VERSION = Version.parse(properties.getProperty(MINIMUM_PLUGIN_VERSION_PROPERTY));
    }

    @Override
    public void apply(@Nonnull Project project) {
        if (!project.equals(project.getRootProject())) {
            throw new GradleException("cordapp-configuration plugin incorrectly applied to '"
                + project.getPath() + "' project. It should only be applied to the root project.");
        }

        project.allprojects(this::validatePlugins);
    }

    private void validatePlugins(@Nonnull Project project) {
        CORDAPP_PLUGIN_IDS.forEach(id ->
            project.getPlugins().withId(id, plugin -> {
                final Version version = getVersionFrom(id, plugin);
                if (version.getBaseVersion().compareTo(MINIMUM_PLUGIN_VERSION) < 0) {
                    throw new StopExecutionException("Plugin " + id + " version " + version
                            + " is below minimum version " + MINIMUM_PLUGIN_VERSION);
                }
            })
        );
    }

    @Nonnull
    private static Version getVersionFrom(String id, @Nonnull Plugin<?> plugin) {
        final URL versionResource = plugin.getClass().getClassLoader().getResource(PLUGIN_VERSION);
        if (versionResource == null) {
            throw new StopExecutionException("Plugin " + id + " has no " + PLUGIN_VERSION);
        }

        final Properties properties = loadProperties(versionResource);
        return Version.parse(properties.getProperty("version"));
    }

    @Nonnull
    private static Properties loadProperties(@Nonnull URL resource) {
        Properties properties = new Properties();
        try (InputStream input = new BufferedInputStream(resource.openStream())) {
            properties.load(input);
        } catch (IOException e) {
            throw new InvalidUserCodeException(e.getMessage(), e);
        }
        return properties;
    }
}
