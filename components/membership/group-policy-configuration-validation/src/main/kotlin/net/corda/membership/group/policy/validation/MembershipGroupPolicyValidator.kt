package net.corda.membership.group.policy.validation

import net.corda.lifecycle.Lifecycle

/**
 * Validator a group policy with the cluster configuration
 */
interface MembershipGroupPolicyValidator: Lifecycle {
    /**
     * Validate of a GroupPolicy with the cluster configuration. Throws exception if validation fails.
     *
     * @param groupPolicy the GroupPolicy as a JSON string.
     *
     * @throws MembershipInvalidGroupPolicyException if validation fails.
     */
    fun validateGroupPolicy(
        groupPolicy: String,
    )
}