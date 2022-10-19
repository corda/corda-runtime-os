package net.corda.ledger.persistence

import net.corda.libs.configuration.SmartConfig

interface ConsensualLedgerProcessorFactory {
    /**
     * Create a new consensual ledger processor.
     */
    fun create(config: SmartConfig): ConsensualLedgerProcessor
}

