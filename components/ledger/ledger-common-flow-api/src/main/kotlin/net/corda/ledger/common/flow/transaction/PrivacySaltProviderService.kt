package net.corda.ledger.common.flow.transaction

import net.corda.ledger.common.data.transaction.PrivacySalt

fun interface PrivacySaltProviderService {

    /**
     * Returns a privacy salt
     */
    fun generatePrivacySalt(): PrivacySalt
}
