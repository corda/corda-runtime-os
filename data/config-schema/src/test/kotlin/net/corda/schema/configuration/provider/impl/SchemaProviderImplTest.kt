package net.corda.schema.configuration.provider.impl

import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.P2P_GATEWAY_CONFIG
import net.corda.schema.configuration.ConfigKeys.P2P_LINK_MANAGER_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.SANDBOX_CONFIG
import net.corda.schema.configuration.ConfigKeys.SECRETS_CONFIG
import net.corda.schema.configuration.ConfigKeys.UTXO_LEDGER_CONFIG
import net.corda.schema.configuration.provider.ConfigSchemaException
import net.corda.schema.configuration.provider.SchemaProviderFactory
import net.corda.v5.base.versioning.Version
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SchemaProviderImplTest {

    companion object {
        // All the top level config keys excluding the boot config, which is handled differently.
        private val CONFIG_KEYS = listOf(
            CRYPTO_CONFIG,
            DB_CONFIG,
            FLOW_CONFIG,
            MESSAGING_CONFIG,
            UTXO_LEDGER_CONFIG,
            P2P_LINK_MANAGER_CONFIG,
            P2P_GATEWAY_CONFIG,
            RPC_CONFIG,
            SECRETS_CONFIG,
            SANDBOX_CONFIG,
            RECONCILIATION_CONFIG,
            MEMBERSHIP_CONFIG,

        )
        private val VERSIONS = listOf("1.0")

        // This is a bit dubious as it assumes that all keys will update schema version at the same time. Either this is
        // true in which case the file structure is wrong, or it is false in which case this function needs to change to
        // account for this. However, this is good enough to bootstrap the schema provider tests.
        @JvmStatic
        private fun schemaSources(): Stream<Arguments> {
            return CONFIG_KEYS.stream().flatMap { key ->
                VERSIONS.stream().map { version -> arguments(key, version) }
            }
        }

        private const val BAD_KEY = "not-a-key"

        private const val SCHEMA_FILE = "net/corda/schema/configuration/test/1.0/schema-fragment.json"
        private const val BAD_SCHEMA_FILE = "foo/bar/does-not-exist.json"
    }

    @ParameterizedTest(name = "schema provider fetches schema for top-level keys: key={0}, version={1}")
    @MethodSource("schemaSources")
    fun `schema provider fetches schema for top-level keys`(key: String, version: String) {
        val provider = SchemaProviderFactory.getSchemaProvider()
        val stream = provider.getSchema(key, Version.fromString(version))
        stream.close()
    }

    @Test
    fun `throws if provided key is not a top-level key`() {
        val provider = SchemaProviderFactory.getSchemaProvider()
        assertThrows<ConfigSchemaException> {
            provider.getSchema(BAD_KEY, Version.fromString("1.0"))
        }
    }

    @Test
    fun `throws if provided version is not valid`() {
        val provider = SchemaProviderFactory.getSchemaProvider()
        assertThrows<ConfigSchemaException> {
            provider.getSchema(MESSAGING_CONFIG, Version(0, 0))
        }
    }

    @Test
    fun `retrieves schema files when specified directly`() {
        val provider = SchemaProviderFactory.getSchemaProvider()
        val stream = provider.getSchemaFile(SCHEMA_FILE)
        stream.close()
    }

    @Test
    fun `throws if provided file does not exist`() {
        val provider = SchemaProviderFactory.getSchemaProvider()
        assertThrows<ConfigSchemaException> {
            provider.getSchemaFile(BAD_SCHEMA_FILE)
        }
    }
}