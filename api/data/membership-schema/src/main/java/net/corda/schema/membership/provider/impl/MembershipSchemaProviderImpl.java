package net.corda.schema.membership.provider.impl;

import net.corda.schema.membership.MembershipSchema;
import net.corda.schema.membership.provider.MembershipSchemaException;
import net.corda.schema.membership.provider.MembershipSchemaProvider;
import net.corda.v5.base.versioning.Version;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class MembershipSchemaProviderImpl implements MembershipSchemaProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(MembershipSchemaProviderImpl.class);

    private static final String RESOURCE_ROOT = "net/corda/schema/membership";
    private static final String SCHEMA_EXTENSION = ".json";
    private static final String CORDA_PREFIX = "corda.";

    @NotNull
    private static String createDirectoryName(@NotNull MembershipSchema schema) {
        final String schemaName = schema.getSchemaName();
        return (schemaName.startsWith(CORDA_PREFIX) ? schemaName.substring(CORDA_PREFIX.length()) : schemaName).replace('.', '/');
    }

    @SuppressWarnings("ConstantValue")
    @Override
    @NotNull
    public InputStream getSchema(@NotNull MembershipSchema schema, @NotNull Version version) {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        } else if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Request for membership schema named {} at version {}", schema.getSchemaName(), version);
        }
        final String directory = createDirectoryName(schema);
        String resource = RESOURCE_ROOT + '/' + directory + '/' + version + '/' + schema.getSchemaName() + SCHEMA_EXTENSION;
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
        LOGGER.trace("Requested schema at {}", resource);
        final URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            final String msg = "Membership schema at " + resource + " cannot be found.";
            LOGGER.error(msg);
            throw new MembershipSchemaException(msg);
        }

        try {
            return url.openStream();
        } catch (IOException e) {
            throw new MembershipSchemaException(e.getMessage(), e);
        }
    }
}
