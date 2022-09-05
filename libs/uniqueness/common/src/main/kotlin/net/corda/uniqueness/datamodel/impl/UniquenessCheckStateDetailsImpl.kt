package net.corda.uniqueness.datamodel.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckStateRef

data class UniquenessCheckStateDetailsImpl(
    override val stateRef: UniquenessCheckStateRef,
    override val consumingTxId: SecureHash?
) : UniquenessCheckStateDetails