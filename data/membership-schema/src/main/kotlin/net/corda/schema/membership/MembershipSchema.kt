package net.corda.schema.membership

/**
 * A membership related schema.
 */
sealed class MembershipSchema {
    /**
     * The name of the schema file.
     */
    abstract val schemaName: String

    /**
     * Schema for the group policy JSON file.
     */
    sealed class GroupPolicySchema : MembershipSchema() {
        /**
         * Default schema for the group policy JSON file.
         */
        object Default : GroupPolicySchema() {
            override val schemaName = "corda.group.policy"
        }
    }

    /**
     * Schema for registration contexts.
     */
    sealed class RegistrationContextSchema : MembershipSchema() {
        /**
         * Schema for static member registration contexts.
         */
        object StaticMember : RegistrationContextSchema() {
            override val schemaName = "corda.member.static.registration"
        }

        /**
         * Schema for dynamic member registration contexts.
         */
        object DynamicMember : RegistrationContextSchema() {
            override val schemaName = "corda.member.dynamic.registration"
        }

        /**
         * Schema for MGM registration contexts.
         */
        object Mgm : RegistrationContextSchema() {
            override val schemaName = "corda.mgm.registration"
        }
    }
}