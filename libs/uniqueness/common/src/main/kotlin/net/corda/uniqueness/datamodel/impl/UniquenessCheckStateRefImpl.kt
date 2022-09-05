package net.corda.uniqueness.datamodel.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckStateRef

data class UniquenessCheckStateRefImpl(
    override val txHash: SecureHash,
    override val stateIndex: Int
) : UniquenessCheckStateRef