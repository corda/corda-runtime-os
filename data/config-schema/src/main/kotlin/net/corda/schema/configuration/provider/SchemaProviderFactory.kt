package net.corda.schema.configuration.provider

import net.corda.schema.configuration.provider.impl.SchemaProviderImpl

/**
 * Factory for schema providers.
 */
object SchemaProviderFactory {

    /**
     * Create a new schema provider.
     *
     * @return The new schema provider.
     */
    fun getSchemaProvider() : SchemaProvider {
        return SchemaProviderImpl()
    }
}