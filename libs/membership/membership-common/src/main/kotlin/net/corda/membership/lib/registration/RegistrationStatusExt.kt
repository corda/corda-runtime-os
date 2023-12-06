package net.corda.membership.lib.registration

import net.corda.data.membership.common.v2.RegistrationStatus

object RegistrationStatusExt {

    val RegistrationStatus.order: Int
        get() =
            when (this) {
                RegistrationStatus.NEW -> 0
                RegistrationStatus.SENT_TO_MGM -> 1
                RegistrationStatus.RECEIVED_BY_MGM -> 1
                RegistrationStatus.STARTED_PROCESSING_BY_MGM -> 2
                RegistrationStatus.PENDING_MEMBER_VERIFICATION -> 3
                RegistrationStatus.PENDING_MANUAL_APPROVAL -> 4
                RegistrationStatus.PENDING_AUTO_APPROVAL -> 4
                RegistrationStatus.DECLINED -> 5
                RegistrationStatus.INVALID -> 5
                RegistrationStatus.FAILED -> 5
                RegistrationStatus.APPROVED -> 5
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
