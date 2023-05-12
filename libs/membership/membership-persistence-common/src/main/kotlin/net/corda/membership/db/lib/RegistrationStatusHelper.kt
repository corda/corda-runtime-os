package net.corda.membership.db.lib

import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.exceptions.MembershipPersistenceException

object RegistrationStatusHelper {
    fun String.toStatus(): RegistrationStatus {
        return RegistrationStatus.values().firstOrNull {
            it.name.equals(this, ignoreCase = true)
        } ?: throw MembershipPersistenceException("Could not find status $this")
    }
}
