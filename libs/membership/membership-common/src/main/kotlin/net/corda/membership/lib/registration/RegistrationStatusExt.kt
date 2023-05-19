package net.corda.membership.lib.registration

import net.corda.data.membership.common.RegistrationStatus

object RegistrationStatusExt {

    private const val FINAL_STATE_VALUE = 5

    val RegistrationStatus.isFinalState: Boolean
        get() = order == FINAL_STATE_VALUE

    val RegistrationStatus.order: Int
        get() =
            when (this) {
                RegistrationStatus.NEW -> 0
                RegistrationStatus.SENT_TO_MGM -> 1
                RegistrationStatus.RECEIVED_BY_MGM -> 2
                RegistrationStatus.PENDING_MEMBER_VERIFICATION -> 3
                RegistrationStatus.PENDING_MANUAL_APPROVAL -> 4
                RegistrationStatus.PENDING_AUTO_APPROVAL -> 4
                RegistrationStatus.DECLINED -> FINAL_STATE_VALUE
                RegistrationStatus.INVALID -> FINAL_STATE_VALUE
                RegistrationStatus.FAILED -> FINAL_STATE_VALUE
                RegistrationStatus.APPROVED -> FINAL_STATE_VALUE
            }

    fun RegistrationStatus.canMoveToStatus(newStatus: RegistrationStatus): Boolean {
        if (newStatus == this) {
            return true
        }
        val currentPhase = order
        val newPhase = newStatus.order
        return (currentPhase < newPhase)
    }
}
