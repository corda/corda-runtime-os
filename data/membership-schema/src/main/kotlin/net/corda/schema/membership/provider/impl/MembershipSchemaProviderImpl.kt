package net.corda.schema.membership.provider.impl

import net.corda.schema.membership.MembershipSchema
import net.corda.schema.membership.provider.MembershipSchemaException
import net.corda.schema.membership.provider.MembershipSchemaProvider
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.base.versioning.Version
import org.slf4j.LoggerFactory
import java.io.InputStream

internal class MembershipSchemaProviderImpl : MembershipSchemaProvider {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)

        private const val RESOURCE_ROOT = "net/corda/schema/membership"
        private const val SCHEMA_EXTENSION = ".json"
        private const val CORDA_PREFIX = "corda."
    }

    override fun getSchema(schema: MembershipSchema, version: Version): InputStream {
        logger.debug { "Request for membership schema named ${schema.schemaName} at version $version" }
        val directory = schema.schemaName
            .removePrefix(CORDA_PREFIX)
            .replace('.', '/')
        val resource = "$RESOURCE_ROOT/$directory/$version/${schema.schemaName}$SCHEMA_EXTENSION"
        return getResourceInputStream(resource)
    }

    override fun getSchemaFile(fileName: String): InputStream {
        return getResourceInputStream(fileName)
    }

    private fun getResourceInputStream(resource: String): InputStream {
        logger.trace { "Requested schema at $resource." }
        val url = this::class.java.classLoader.getResource(resource)
        if (url == null) {
            val msg = "Membership schema at $resource cannot be found."
            logger.error(msg)
            throw MembershipSchemaException(msg)
        }
        return url.openStream()
    }
}