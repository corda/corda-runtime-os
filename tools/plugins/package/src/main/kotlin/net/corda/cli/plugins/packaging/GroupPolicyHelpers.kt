package net.corda.cli.plugins.packaging

import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.impl.MembershipSchemaValidatorImpl
import net.corda.schema.membership.MembershipSchema
import net.corda.schema.membership.provider.MembershipSchemaProviderFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.versioning.Version
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal object GroupPolicyHelpers {
    /**
     * Filename of group policy within jar file
     */
    private val META_INF_GROUP_POLICY_JSON = "META-INF/GroupPolicy.json"

    /**
     * Adds group policy file to jar file
     *
     * Reads group policy from stdin or file depending on user choice
     */
    fun addGroupPolicy(cpiJar: JarOutputStream, groupPolicy: String) {
        cpiJar.putNextEntry(JarEntry(META_INF_GROUP_POLICY_JSON))

        groupPolicy.byteInputStream().copyTo(cpiJar)

        cpiJar.closeEntry()
    }

    /**
     * Validates group policy against schema.
     */
    fun validateGroupPolicy(groupPolicyString: String)  {
        val membershipSchemaValidator = MembershipSchemaValidatorImpl(MembershipSchemaProviderFactory.getSchemaProvider())

        var fileFormatVersion: Int
        try {
            fileFormatVersion = GroupPolicyParser.getFileFormatVersion(groupPolicyString)
        } catch (e: CordaRuntimeException) {
            throw MembershipSchemaValidationException(
                "Exception when validating group policy. Group policy file is invalid. Could not get file format version. ",
                e,
                MembershipSchema.GroupPolicySchema.Default,
                listOf("${e.message}"))
        }

        membershipSchemaValidator.validateGroupPolicy(
            MembershipSchema.GroupPolicySchema.Default,
            Version(fileFormatVersion, 0),
            groupPolicyString
        )
    }
}