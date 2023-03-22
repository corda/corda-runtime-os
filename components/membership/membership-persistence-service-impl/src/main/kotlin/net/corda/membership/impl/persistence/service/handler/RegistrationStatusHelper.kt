package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.exceptions.MembershipPersistenceException

internal object RegistrationStatusHelper {

    fun String.toStatus(): RegistrationStatus {
        return RegistrationStatus.values().firstOrNull {
            it.name.equals(this, ignoreCase = true)
        } ?: throw MembershipPersistenceException("Could not find status $this")
    }

    private fun RegistrationStatus.getOrder(): Int {
        return when (this) {
            RegistrationStatus.NEW -> 0
            RegistrationStatus.SENT_TO_MGM -> 1
            RegistrationStatus.RECEIVED_BY_MGM -> 1
            RegistrationStatus.PENDING_MEMBER_VERIFICATION -> 2
            RegistrationStatus.PENDING_MANUAL_APPROVAL -> 3
            RegistrationStatus.PENDING_AUTO_APPROVAL -> 3
            RegistrationStatus.DECLINED -> 4
            RegistrationStatus.INVALID -> 4
            RegistrationStatus.APPROVED -> 4
        }
    }

    fun RegistrationStatus.canMoveToStatus(newStatus: RegistrationStatus): Boolean {
        if (newStatus == this) {
            return true
        }
        val currentPhase = getOrder()
        val newPhase = newStatus.getOrder()
        return (currentPhase < newPhase)
    }
}
