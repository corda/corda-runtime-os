package net.corda.schema.membership;

import org.jetbrains.annotations.NotNull;

/**
 * A membership related schema.
 */
public abstract class MembershipSchema {
    private MembershipSchema(String schemaName) {
        this.schemaName = schemaName;
    }

    private final String schemaName;

    @NotNull
    public final String getSchemaName() {
        return schemaName;
    }

    /**
     * Schema for the group policy JSON file.
     */
    public static final class GroupPolicySchema extends MembershipSchema {
        private GroupPolicySchema(String schemaName) {
            super(schemaName);
        }

        /**
         * Default schema for the group policy JSON file.
         */
        public static final GroupPolicySchema Default = new GroupPolicySchema("corda.group.policy");
    }

    /**
     * Schema for registration contexts.
     */
    public static final class RegistrationContextSchema extends MembershipSchema {
        private RegistrationContextSchema(String schemaName) {
            super(schemaName);
        }

        /**
         * Schema for static member registration contexts.
         */
        public static final RegistrationContextSchema StaticMember = new RegistrationContextSchema("corda.member.static.registration");

        /**
         * Schema for dynamic member registration contexts.
         */
        public static final RegistrationContextSchema DynamicMember = new RegistrationContextSchema("corda.member.dynamic.registration");

        /**
         * Schema for MGM registration contexts.
         */
        public static final RegistrationContextSchema Mgm = new RegistrationContextSchema("corda.mgm.registration");
    }
}
