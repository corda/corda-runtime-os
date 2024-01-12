package net.corda.ledger.common.flow.transaction

import net.corda.ledger.common.data.transaction.PrivacySalt

interface PrivacySaltProviderService {

    fun generatePrivacySalt(): PrivacySalt
}