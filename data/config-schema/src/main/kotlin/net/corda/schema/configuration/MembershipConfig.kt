package net.corda.schema.configuration

/**
 * Membership configuration keys
 */
object MembershipConfig {
    /**
     * The configuration key to get the maximal duration between two synchronization requests
     */
    const val MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES = "maxDurationBetweenSyncRequestsMinutes"

    object TtlsConfig {
        /**
         * The configuration key to get the TTLs durations
         */
        const val TTLS = "TTLs"

        /**
         * Maximum duration in minutes between to wait for a members package update message.
         */
        const val MEMBERS_PACKAGE_UPDATE = "membersPackageUpdate"

        /**
         * Maximum duration in minutes between to wait for a decline registration message.
         */
        const val DECLINE_REGISTRATION = "declineRegistration"

        /**
         * Maximum duration in minutes between to wait for an update registration status to pending auto approval.
         */
        const val UPDATE_TO_PENDING_AUTO_APPROVAL = "updateToPendingAutoApproval"

        /**
         * Maximum duration in minutes between to wait for a verify member request.
         */
        const val VERIFY_MEMBER_REQUEST = "verifyMemberRequest"
    }
}
