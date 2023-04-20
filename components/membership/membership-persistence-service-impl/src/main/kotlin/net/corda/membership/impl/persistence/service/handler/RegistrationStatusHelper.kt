package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.exceptions.MembershipPersistenceException

internal object RegistrationStatusHelper {

    fun String.toStatus(): RegistrationStatus {
        return RegistrationStatus.values().firstOrNull {
            it.name.equals(this, ignoreCase = true)
        } ?: throw MembershipPersistenceException("Could not find status $this")
    }
}
