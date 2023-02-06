package net.corda.membership.client.dto

enum class RegistrationStatusDto {
    NEW,
    SENT_TO_MGM,
    RECEIVER_BY_MGM,
    PENDING_MEMBER_VERIFICATION,
    PENDING_APPROVAL_FLOW,
    PENDING_MANUAL_APPROVAL,
    PENDING_AUTO_APPROVAL,
    DECLINED,
    INVALID,
    APPROVED
}
