package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission

import net.corda.v5.base.exceptions.CordaRuntimeException

class UnverifiedTransactionSentException(message: String) : CordaRuntimeException(message, null)