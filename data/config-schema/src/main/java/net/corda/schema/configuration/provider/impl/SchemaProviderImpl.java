package net.corda.schema.configuration.provider.impl;

import net.corda.schema.configuration.provider.ConfigSchemaException;
import net.corda.schema.configuration.provider.SchemaProvider;
import net.corda.v5.base.versioning.Version;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public final class SchemaProviderImpl implements SchemaProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaProviderImpl.class);

    private static final String RESOURCE_ROOT = "net/corda/schema/configuration";
    private static final String SCHEMA_EXTENSION = ".json";
    private static final String CORDA_PREFIX = "corda.";

    @NotNull
    private static String removePrefix(@NotNull String key) {
        return key.startsWith(CORDA_PREFIX) ? key.substring(CORDA_PREFIX.length()) : key;
    }

    @SuppressWarnings("ConstantValue")
    @Override
    @NotNull
    public InputStream getSchema(@NotNull String key, @NotNull Version version) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        } else if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Request for schema for config key {} at version {}", key, version);
        }
        final String directory = removePrefix(key);
        final String resource = RESOURCE_ROOT + '/' + directory + '/' + version + '/' + key + SCHEMA_EXTENSION;
        return getResourceInputStream(resource);
    }

    @SuppressWarnings("ConstantValue")
    @Override
    @NotNull
    public InputStream getSchemaFile(@NotNull String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("fileName must not be null");
        }
        return getResourceInputStream(fileName);
    }

    @NotNull
    private InputStream getResourceInputStream(@NotNull String resource) {
        LOGGER.trace("Requested schema at {}.", resource);
        final URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            final String msg = "Config schema at " + resource + " cannot be found.";
            LOGGER.error(msg);
            throw new ConfigSchemaException(msg);

        }

        try {
            return url.openStream();
        } catch (IOException e) {
            throw new ConfigSchemaException(e.getMessage(), e);
        }
    }
}
