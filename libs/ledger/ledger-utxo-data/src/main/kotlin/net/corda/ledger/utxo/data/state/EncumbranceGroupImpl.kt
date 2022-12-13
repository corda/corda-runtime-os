package net.corda.ledger.utxo.data.state

import net.corda.v5.ledger.utxo.EncumbranceGroup

data class EncumbranceGroupImpl(override val size: Int, override val tag: String) : EncumbranceGroup