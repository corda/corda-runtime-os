package net.corda.schema.common.provider.impl;

import net.corda.schema.common.provider.SchemaProvider;
import net.corda.v5.base.versioning.Version;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.InputStream;

public abstract class AbstractSchemaProvider implements SchemaProvider {

    protected AbstractSchemaProvider(final Logger logger) {
        this.logger = logger;
    }

    protected final Logger logger;

    private static final String SCHEMA_EXTENSION = ".json";
    private static final String CORDA_PREFIX = "corda.";

    public abstract String getResourceRoot();

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
        if (logger.isDebugEnabled()) {
            logger.debug("Request for schema for config key {} at version {}", key, version);
        }
        final String directory = removePrefix(key);
        final String resource = getResourceRoot() + '/' + directory + '/' + version + '/' + key + SCHEMA_EXTENSION;
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
    protected abstract InputStream getResourceInputStream(@NotNull String resource);
}
