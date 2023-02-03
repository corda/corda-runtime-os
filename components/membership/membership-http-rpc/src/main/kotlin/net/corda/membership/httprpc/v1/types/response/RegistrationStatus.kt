package net.corda.membership.httprpc.v1.types.response

enum class RegistrationStatus {
    NEW,
    SENT_TO_MGM,
    PENDING_MGM_NETWORK_ACCESS,
    PENDING_MEMBER_VERIFICATION,
    PENDING_APPROVAL_FLOW,
    PENDING_MANUAL_APPROVAL,
    PENDING_AUTO_APPROVAL,
    DECLINED,
    INVALID,
    APPROVED
}
