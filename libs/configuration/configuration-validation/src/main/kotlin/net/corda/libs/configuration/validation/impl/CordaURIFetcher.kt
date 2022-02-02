package net.corda.libs.configuration.validation.impl

import com.networknt.schema.uri.URIFetcher
import net.corda.libs.configuration.validation.ConfigurationSchemaFetchException
import net.corda.schema.configuration.ConfigSchemaException
import net.corda.schema.configuration.SchemaProviderFactory
import java.io.InputStream
import java.net.URI

internal class CordaURIFetcher : URIFetcher {

    private companion object {
        private const val CORDA_SCHEMA_URL = "https://corda.r3.com/"
    }

    private val schemaProvider = SchemaProviderFactory().getSchemaProvider()

    override fun fetch(uri: URI?): InputStream {
        if (uri == null) {
            throw ConfigurationSchemaFetchException("Schema validator requested a null URI to fetch.")
        }
        val resource = uri.toString().removePrefix(CORDA_SCHEMA_URL)
        return try {
            schemaProvider.getSchemaFile(resource)
        } catch (e: ConfigSchemaException) {
            throw ConfigurationSchemaFetchException("Could not fetch schema at resource $resource", e)
        }
    }
}