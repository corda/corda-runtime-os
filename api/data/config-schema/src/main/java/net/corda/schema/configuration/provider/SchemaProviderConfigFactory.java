package net.corda.schema.configuration.provider;

import net.corda.schema.common.provider.SchemaProvider;
import net.corda.schema.configuration.provider.impl.SchemaProviderConfigImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for schema providers.
 */
public final class SchemaProviderConfigFactory {
    private SchemaProviderConfigFactory() {
    }

    /**
     * Create a new schema provider.
     *
     * @return The new schema provider.
     */
    @NotNull
    public static SchemaProvider getSchemaProvider() {
        return new SchemaProviderConfigImpl();
    }
}
