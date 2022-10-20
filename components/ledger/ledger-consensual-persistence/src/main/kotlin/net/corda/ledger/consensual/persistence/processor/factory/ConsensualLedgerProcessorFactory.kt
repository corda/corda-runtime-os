package net.corda.ledger.consensual.persistence.processor.factory

import net.corda.ledger.consensual.persistence.processor.ConsensualLedgerProcessor
import net.corda.libs.configuration.SmartConfig

interface ConsensualLedgerProcessorFactory {
    /**
     * Create a new consensual ledger processor.
     */
    fun create(config: SmartConfig): ConsensualLedgerProcessor
}

