package net.corda.membership.lib.schema.validation

/**
 * Factory class for creating instances of [MembershipSchemaValidator]
 */
interface MembershipSchemaValidatorFactory {
    /**
     * Creates a new membership schema validator.
     *
     * @return new instance of [MembershipSchemaValidator]
     */
    fun createValidator(): MembershipSchemaValidator
}