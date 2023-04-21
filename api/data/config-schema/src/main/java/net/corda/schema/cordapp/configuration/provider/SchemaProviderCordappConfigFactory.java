package net.corda.schema.cordapp.configuration.provider;

import net.corda.schema.common.provider.SchemaProvider;
import net.corda.schema.cordapp.configuration.provider.impl.SchemaProviderCordappConfigImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for schema providers.
 */
public final class SchemaProviderCordappConfigFactory {
    private SchemaProviderCordappConfigFactory() {
    }

    /**
     * Create a new schema provider.
     *
     * @return The new schema provider.
     */
    @NotNull
    public static SchemaProvider getSchemaProvider() {
        return new SchemaProviderCordappConfigImpl();
    }
}
