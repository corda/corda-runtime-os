package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.base.exceptions.CordaRuntimeException

class InvalidBackchainException(message: String) : CordaRuntimeException(message, null)
