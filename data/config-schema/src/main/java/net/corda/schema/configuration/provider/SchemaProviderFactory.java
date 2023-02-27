package net.corda.schema.configuration.provider;

import net.corda.schema.configuration.provider.impl.SchemaProviderImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for schema providers.
 */
public final class SchemaProviderFactory {
    private SchemaProviderFactory() {
    }

    /**
     * Create a new schema provider.
     *
     * @return The new schema provider.
     */
    @NotNull
    public static SchemaProvider getSchemaProvider() {
        return new SchemaProviderImpl();
    }
}
