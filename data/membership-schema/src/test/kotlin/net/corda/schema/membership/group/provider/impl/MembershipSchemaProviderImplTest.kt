package net.corda.schema.membership.group.provider.impl

import net.corda.schema.membership.MembershipSchema
import net.corda.schema.membership.MembershipSchema.GroupPolicySchema
import net.corda.schema.membership.MembershipSchema.RegistrationContextSchema
import net.corda.schema.membership.provider.MembershipSchemaException
import net.corda.schema.membership.provider.MembershipSchemaProviderFactory
import net.corda.v5.base.versioning.Version
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class MembershipSchemaProviderImplTest {

    companion object {
        // All membership schemas and their supported versions
        private val SCHEMA_VERSIONS = listOf(
            GroupPolicySchema.Default to "1.0",
            RegistrationContextSchema.StaticMember to "1.0",
            RegistrationContextSchema.DynamicMember to "1.0",
            RegistrationContextSchema.Mgm to "1.0"
        )

        @JvmStatic
        private fun schemaToVersion() = SCHEMA_VERSIONS.stream().map { arguments(it.first, it.second) }

        @JvmStatic
        private fun schemas() = SCHEMA_VERSIONS.stream().map { arguments(it.first) }

        private const val SCHEMA_FILE = "net/corda/schema/membership/test/1.0/schema-fragment.json"
        private const val BAD_SCHEMA_FILE = "foo/bar/does-not-exist.json"

    }

    @ParameterizedTest(name = "membership schema provider fetches schema: schema={0}, version={1}")
    @MethodSource("schemaToVersion")
    fun `membership schema provider fetches schema`(schema: MembershipSchema, version: String) {
        val provider = MembershipSchemaProviderFactory.getSchemaProvider()
        assertDoesNotThrow {
            provider.getSchema(schema, Version.fromString(version))
        }.close()
    }

    @ParameterizedTest(name = "throws if provided version is not valid: schema={0}")
    @MethodSource("schemas")
    fun `throws if provided version is not valid`(schema: MembershipSchema) {
        val provider = MembershipSchemaProviderFactory.getSchemaProvider()
        assertThrows<MembershipSchemaException> {
            provider.getSchema(schema, Version(-1, -1))
        }
    }

    @Test
    fun `retrieves schema files when specified directly`() {
        val provider = MembershipSchemaProviderFactory.getSchemaProvider()
        val stream = assertDoesNotThrow { provider.getSchemaFile(SCHEMA_FILE) }
        stream.close()
    }

    @Test
    fun `throws if provided file does not exist`() {
        val provider = MembershipSchemaProviderFactory.getSchemaProvider()
        assertThrows<MembershipSchemaException> {
            provider.getSchemaFile(BAD_SCHEMA_FILE)
        }
    }
}