package net.corda.membership.lib.group.policy.validation

import net.corda.libs.configuration.SmartConfig

/**
 * Validator a group policy with the cluster configuration
 */
interface MembershipGroupPolicyValidator {
    /**
     * Validate a GroupPolicy with the cluster configuration. Throws exception if validation fails.
     *
     * @param groupPolicy the GroupPolicy as a JSON string.
     * @param configurationGetService A service to get the cluster configuration.
     *
     * @throws MembershipInvalidGroupPolicyException if validation fails.
     */
    fun validateGroupPolicy(
        groupPolicy: String,
        configurationGetService : ((String) -> SmartConfig?),
    )
}