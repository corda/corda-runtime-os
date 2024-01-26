package net.corda.ledger.common.flow.transaction

import net.corda.ledger.common.data.transaction.PrivacySalt

fun interface PrivacySaltProviderService {

    /**
     * Returns a deterministic unique salt based on a flows information.
     */
    fun generatePrivacySalt(): PrivacySalt
}
