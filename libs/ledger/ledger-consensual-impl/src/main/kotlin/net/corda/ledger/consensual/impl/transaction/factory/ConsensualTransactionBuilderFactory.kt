package net.corda.ledger.consensual.impl.transaction.factory

import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

interface ConsensualTransactionBuilderFactory {

    fun create(): ConsensualTransactionBuilder
}