package net.corda.membership.lib.group.policy.validation

/**
 * Factory class for creating instances of [MembershipGroupPolicyValidator]
 */
interface MembershipGroupPolicyValidatorFactory {
    /**
     * Creates a new membership schema validator.
     *
     * @return new instance of [MembershipGroupPolicyValidator]
     */
    fun createValidator(): MembershipGroupPolicyValidator
}