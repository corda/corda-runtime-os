package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash

data class UniquenessCheckStateDetailsImpl(
    private val stateRef: UniquenessCheckStateRef,
    private val consumingTxId: SecureHash?
) : UniquenessCheckStateDetails {
    override fun getStateRef() = stateRef
    override fun getConsumingTxId() = consumingTxId
}
