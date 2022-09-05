package net.corda.membership.httprpc.v1.types.response

enum class RegistrationStatus {
    NEW,
    PENDING_MEMBER_VERIFICATION,
    PENDING_APPROVAL_FLOW,
    PENDING_MANUAL_APPROVAL,
    PENDING_AUTO_APPROVAL,
    DECLINED,
    APPROVED
}
