package net.corda.membership.client.dto

enum class RegistrationStatusDto {
    NEW,
    SENT_TO_MGM,
    RECEIVED_BY_MGM,
    STARTED_PROCESSING_BY_MGM,
    PENDING_MEMBER_VERIFICATION,
    PENDING_MANUAL_APPROVAL,
    PENDING_AUTO_APPROVAL,
    DECLINED,
    INVALID,
    FAILED,
    APPROVED,
}
