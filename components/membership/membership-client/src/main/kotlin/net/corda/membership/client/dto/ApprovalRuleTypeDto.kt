package net.corda.membership.client.dto

enum class ApprovalRuleTypeDto {
    /**
     * Approval rule for registration requests without a pre-auth token.
     */
    STANDARD,
    /**
     * Approval rule for registration requests with a valid pre-auth token.
     */
    PREAUTH,
}