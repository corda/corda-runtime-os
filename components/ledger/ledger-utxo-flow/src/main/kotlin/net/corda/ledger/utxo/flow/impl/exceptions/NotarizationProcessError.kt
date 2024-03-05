package net.corda.ledger.utxo.flow.impl.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class NotarizationProcessError(message: String) : CordaRuntimeException(message)