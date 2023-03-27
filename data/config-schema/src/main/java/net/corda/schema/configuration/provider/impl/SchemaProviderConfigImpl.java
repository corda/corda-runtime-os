package net.corda.schema.configuration.provider.impl;

import net.corda.schema.common.provider.impl.AbstractSchemaProvider;
import net.corda.schema.configuration.provider.ConfigSchemaException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public final class SchemaProviderConfigImpl extends AbstractSchemaProvider {

    public SchemaProviderConfigImpl() {
        super(LoggerFactory.getLogger(SchemaProviderConfigImpl.class));
    }

    @Override
    public String getResourceRoot() {
        return "net/corda/schema/configuration";
    }

    @NotNull
    protected InputStream getResourceInputStream(@NotNull String resource) {
        logger.trace("Requested schema at {}.", resource);
        final URL url = getClass().getClassLoader().getResource(resource);
        if (url == null) {
            final String msg = "Config schema at " + resource + " cannot be found.";
            logger.error(msg);
            throw new ConfigSchemaException(msg);

        }

        try {
            return url.openStream();
        } catch (IOException e) {
            throw new ConfigSchemaException(e.getMessage(), e);
        }
    }
}
