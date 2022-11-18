package net.corda.cli.plugins.packaging

import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.schema.validation.impl.MembershipSchemaValidatorImpl
import net.corda.schema.membership.MembershipSchema
import net.corda.schema.membership.provider.MembershipSchemaProviderFactory
import net.corda.v5.base.versioning.Version

internal object GroupPolicyValidator {
    /**
     * Validates group policy against schema.
     */
    fun validateGroupPolicy(groupPolicyString: String)  {
        val fileFormatVersion = GroupPolicyParser.getFileFormatVersion(groupPolicyString)

        val membershipSchemaValidator = MembershipSchemaValidatorImpl(MembershipSchemaProviderFactory.getSchemaProvider())
        membershipSchemaValidator.validateGroupPolicy(
            MembershipSchema.GroupPolicySchema.Default,
            Version(fileFormatVersion, 0),
            groupPolicyString
        )
    }
}