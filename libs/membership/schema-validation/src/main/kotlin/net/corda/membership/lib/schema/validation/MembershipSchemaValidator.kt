package net.corda.membership.lib.schema.validation

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.membership.MembershipSchema
import net.corda.v5.base.versioning.Version

/**
 * Validator for membership specific inputs. Validated against schemas in the corda-api repo.
 */
interface MembershipSchemaValidator {
    /**
     * Validate a GroupPolicy JSON string against a membership schema. Throws exception if validation fails.
     * Otherwise validation was successful.
     *
     * @param schema the schema to validate against.
     * @param version the schema version to validate against.
     * @param groupPolicy the GroupPolicy as a JSON string.
     * @param configurationGetService A service to get the cluster configuration. Set to null
     * to skip the TLS type validation
     *
     * @throws MembershipSchemaValidationException if validation fails.
     */
    fun validateGroupPolicy(
        schema: MembershipSchema.GroupPolicySchema,
        version: Version,
        groupPolicy: String,
        configurationGetService : ((String) -> SmartConfig?)?,
    )

    /**
     * Validate a registration context against a membership schema.Throws exception if validation fails.
     * Otherwise validation was successful.
     *
     * @param schema the schema to validate against.
     * @param version the schema version to validate against.
     * @param registrationContext registration context map given at time of registration.
     *
     * @throws MembershipSchemaValidationException if validation fails.
     */
    fun validateRegistrationContext(
        schema: MembershipSchema.RegistrationContextSchema,
        version: Version,
        registrationContext: Map<String, String>
    )
}