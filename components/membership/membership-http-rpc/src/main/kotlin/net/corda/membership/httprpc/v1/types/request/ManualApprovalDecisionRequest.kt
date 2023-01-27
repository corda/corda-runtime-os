package net.corda.membership.httprpc.v1.types.request

/**
 * Decision taken on a registration request which requires manual approval.
 *
 * @param action The approval action [ManualApprovalAction].
 * @param reason Optional. Reason for the specified [action].
 */
data class ManualApprovalDecisionRequest(
    val action: ManualApprovalAction,
    val reason: String? = null,
)

enum class ManualApprovalAction {
    APPROVE, DECLINE
}
