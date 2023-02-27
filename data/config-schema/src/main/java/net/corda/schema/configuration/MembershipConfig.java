package net.corda.schema.configuration;

/**
 * Membership configuration keys
 */
public final class MembershipConfig {
    private MembershipConfig() {
    }

    /**
     * The configuration key to get the maximal duration between two synchronization requests
     */
    public static final String MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES = "maxDurationBetweenSyncRequestsMinutes";

    public static final class TtlsConfig {
        private TtlsConfig() {
        }

        /**
         * The configuration key to get the TTLs durations
         */
        public static final String TTLS = "TTLs";

        /**
         * Maximum duration in minutes between to wait for a members package update message.
         */
        public static final String MEMBERS_PACKAGE_UPDATE = "membersPackageUpdate";

        /**
         * Maximum duration in minutes between to wait for a decline registration message.
         */
        public static final String DECLINE_REGISTRATION = "declineRegistration";

        /**
         * Maximum duration in minutes between to wait for an update registration status to pending auto approval.
         */
        public static final String UPDATE_TO_PENDING_AUTO_APPROVAL = "updateToPendingAutoApproval";

        /**
         * Maximum duration in minutes between to wait for a verify member request.
         */
        public static final String VERIFY_MEMBER_REQUEST = "verifyMemberRequest";
    }
}
