package net.corda.osgi.framework;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleRevision;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toList;
import static org.osgi.framework.wiring.BundleRevision.TYPE_FRAGMENT;

class OSGiFrameworkUtils {
    /**
     * Location of the file listing the extra system packages exposed from the JDK to the framework.
     * See <a href="http://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html#framework.lifecycle.launchingproperties">OSGi Core Release 7 - 4.2.2 Launching Properties</a>
     * The location is relative to run time class path:
     * <ul>
     * <li>{@code build/resources/main} in a gradle project</li>
     * <li>the root of the fat executable {@code .jar}</li>
     * </ul>
     */
    private static final String SYSTEM_PACKAGES_EXTRA = "system_packages_extra";
    private static final String FRAMEWORK_PROPERTIES_RESOURCE = "framework.properties";

    /**
     * Return `true` if the {@code state} LSB is between {@link Bundle#STARTING} and {@link Bundle#ACTIVE} excluded
     * because the bundle is stoppable if {@link Bundle#getState} is in this range.
     * <p>
     * Bundle states are expressed as a bit-mask though a bundle can only be in one state at any time,
     * the state in the lifecycle is represented in the LSB of the value returned by {@link Bundle#getState}.
     * See OSGi Core Release 7 <a href="https://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html">4.4.2 Bundle State</a>
     *
     * @param state of the bundle.
     * @return {@code true} if the {@code state} LSB is between {@link Bundle#STARTING} and {@link Bundle#ACTIVE} excluded.
     */
    static boolean isStoppable(int state) {
        // The bundle lifecycle state is represented by LSB.
        final int status = state & 0xff;
        return status > Bundle.STARTING && state <= Bundle.ACTIVE;
    }

    /**
     * Return {@code true} if the {@code bundle} is an
     * OSGi <a href="https://www.osgi.org/developer/white-papers/semantic-versioning/bundles-and-fragments/">fragment</a>.
     * OSGi fragments are not subject to activation.
     *
     * @param bundle to check if it is fragment.
     * @return Return {@code true} if the {@code bundle} is an OSGi fragment.
     */
    static boolean isFragment(Bundle bundle) {
        return (bundle.adapt(BundleRevision.class).getTypes() & TYPE_FRAGMENT) != 0;
    }

    /**
     * Return {@code true} if the {@code state} LSB is {@link Bundle#ACTIVE}
     * <p>
     * Bundle states are expressed as a bit-mask though a bundle can only be in one state at any time,
     * the state in the lifecycle is represented in the LSB of the value returned by {@link Bundle#getState}.
     * See OSGi Core Release 7 <a href="https://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html">4.4.2 Bundle State</a>
     *
     * @param state of the bundle.
     * @return {@code true} if the {@code state} LSB is {@link Bundle#ACTIVE}.
     */
    static Boolean isActive(int state) {
        // The bundle lifecycle state is represented by LSB.
        return (state & 0xff) == Bundle.ACTIVE;
    }

    /**
     * Return {@code true} if the {@code state} LSB is between {@link Bundle#UNINSTALLED} and {@link Bundle#STOPPING} excluded
     * because the bundle is startable if {@link Bundle#getState} is inside this range.
     * <p>
     * Bundle states are expressed as a bit-mask though a bundle can only be in one state at any time,
     * the state in the lifecycle is represented in the LSB of the value returned by {@link Bundle#getState}.
     * See OSGi Core Release 7 <a href="https://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html">4.4.2 Bundle State</a>
     *
     * @param state of the bundle.
     * @return {@code true} if the {@code state} LSB is between {@link Bundle#UNINSTALLED} and {@link Bundle#STOPPING} excluded.
     */
    static boolean isStartable(int state) {
        // The bundle lifecycle state is represented by LSB.
        final int status = state & 0xff;
        return status > Bundle.UNINSTALLED && status < Bundle.STOPPING;
    }

    /**
     * Return a new configured {@link Framework} loaded from the classpath and having {@code frameworkFactoryFQN} as
     * Full Qualified Name of the {@link FrameworkFactory}.
     * Configure the {@link Framework} to set the bundles' cache to {@code frameworkStorageDir} path.
     * <p>
     * The {@link FrameworkFactory} must be in the classpath.
     *
     * @param frameworkStorageDir Path to the directory the {@link Framework} uses as bundles' cache.
     * @param classLoader         Classloader to use as a starting point to locate service providers for the OSGI FrameworkFactory class
     * @param logger              A logger instance which this method will use
     * @return A new configured {@link Framework} loaded from the classpath and having {@code frameworkFactoryFQN} as
     * Full Qualified Name of the {@link FrameworkFactory}.
     * @throws ClassNotFoundException If the {@link FrameworkFactory} specified in {@code frameworkFactoryFQN}
     *                                isn't in the classpath.
     * @throws SecurityException      If a {@link SecurityManager} is installed and the caller hasn't {@link RuntimePermission}.
     * @see <a href="http://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html#framework.lifecycle.launchingproperties">OSGi Core Release 7 - 4.2.2 Launching Properties</a>
     * See {@link #getFrameworkPropertyFrom(String)} to load properties from resources.
     */
    static Framework getFrameworkFrom(
            Path frameworkStorageDir,
            ClassLoader classLoader,
            Logger logger
    ) throws ClassNotFoundException, IOException, SecurityException {
        Optional<FrameworkFactory> optFactory = ServiceLoader.load(FrameworkFactory.class, classLoader).findFirst();
        if (optFactory.isEmpty()) {
            throw new ClassNotFoundException("No OSGi FrameworkFactory found.");
        }
        final FrameworkFactory frameworkFactory = optFactory.get();
        final Map<String, String> configurationMap = new LinkedHashMap<>();
        configurationMap.put(Constants.FRAMEWORK_STORAGE, frameworkStorageDir.toString());
        configurationMap.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        configurationMap.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, getFrameworkPropertyFrom(SYSTEM_PACKAGES_EXTRA));
        configurationMap.putAll(toStringMap(loadOSGiProperties(FRAMEWORK_PROPERTIES_RESOURCE)));
        configurationMap.putAll(toStringMap(System.getProperties()));
        if (logger.isDebugEnabled()) {
            configurationMap.forEach((key, value) -> logger.debug("OSGi property {} = {}.", key, value));
        }
        return frameworkFactory.newFramework(configurationMap);
    }

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return unmodifiableMap(result);
    }

    /**
     * @param resource in the classpath containing a properties file.
     * @return a {@link Properties} object.
     * @throws IOException Failed to read OSGi properties.
     */
    private static Properties loadOSGiProperties(String resource) throws IOException {
        final Properties properties = new Properties();
        final URL resourceUrl = OSGiFrameworkMain.class.getClassLoader().getResource(resource);
        if (resourceUrl != null) {
            try (InputStream input = new BufferedInputStream(resourceUrl.openStream())) {
                properties.load(input);
            }
        }
        return properties;
    }

    /**
     * Return the {@code resource} as a comma separated list to be used as a property to configure the OSGi framework.
     * Ignore anything in a line after `#`.
     *
     * @param resource in the classpath from where to read the list.
     * @return the list loaded from {@code resource} as a comma separated text value.
     * @throws IOException If the {@code resource} can't be accessed.
     */
    private static String getFrameworkPropertyFrom(String resource) throws IOException {
        final URL resourceUrl = OSGiFrameworkMain.class.getClassLoader().getResource(resource);
        if (resourceUrl == null) {
            throw new IOException("OSGi property resource " + resource + " not found in this classpath/jar.");
        }
        final List<String> propertyValueList;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream()))) {
            propertyValueList = reader.lines().map(OSGiFrameworkUtils::removeTrailingComment)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(toList());
        }
        return String.join(",", propertyValueList);
    }

    static String removeTrailingComment(String line) {
        final int commentIdx = line.indexOf('#');
        return (commentIdx < 0) ? line : line.substring(0, commentIdx);
    }
}
