package net.corda.ledger.persistence.common

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.persistence.common.exceptions.MissingAccountContextPropertyException

object LedgerPersistenceUtils {

    private const val CORDA_ACCOUNT = "corda.account"
    private const val CORDA_INITIATOR_ACCOUNT = "corda.initiator.account"

    fun ExternalEventContext.findAccount(): String {
        return contextProperties.items.find { it.key == CORDA_ACCOUNT || it.key == CORDA_INITIATOR_ACCOUNT }?.value
            ?: throw MissingAccountContextPropertyException()
    }
}
