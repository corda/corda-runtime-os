package net.corda.uniqueness.datamodel.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails

data class UniquenessCheckStateDetailsImpl(
    override val stateRef: StateRef,
    override val consumingTxId: SecureHash?
) : UniquenessCheckStateDetails