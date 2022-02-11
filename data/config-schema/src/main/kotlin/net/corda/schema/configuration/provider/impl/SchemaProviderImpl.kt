package net.corda.schema.configuration.provider.impl

import net.corda.schema.configuration.provider.ConfigSchemaException
import net.corda.schema.configuration.provider.SchemaProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.base.versioning.Version
import org.osgi.framework.FrameworkUtil
import java.io.InputStream

internal class SchemaProviderImpl : SchemaProvider {

    companion object {
        private val logger = contextLogger()

        private const val RESOURCE_ROOT = "net/corda/schema/configuration"
        private const val SCHEMA_EXTENSION = ".json"
        private const val CORDA_PREFIX = "corda."
    }

    override fun getSchema(key: String, version: Version): InputStream {
        logger.debug { "Request for schema for config key $key at version $version" }
        val directory = key.removePrefix(CORDA_PREFIX)
        val resource = "$RESOURCE_ROOT/$directory/$version/$key$SCHEMA_EXTENSION"
        return getResourceInputStream(resource)
    }

    override fun getSchemaFile(fileName: String): InputStream {
        return getResourceInputStream(fileName)
    }

    private fun getResourceInputStream(resource: String): InputStream {
        logger.trace { "Requested schema at $resource." }
        val bundle = FrameworkUtil.getBundle(this::class.java)
        val url = bundle?.getResource(resource) ?: this::class.java.classLoader.getResource(resource)
        if (url == null) {
            val msg = "Config schema at $resource cannot be found."
            logger.error(msg)
            throw ConfigSchemaException(msg)

        }
        return url.openStream()
    }
}