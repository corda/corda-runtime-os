package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash

data class UniquenessCheckStateRefImpl(
    private val txHash: SecureHash,
    private val stateIndex: Int
) : UniquenessCheckStateRef {
    override fun getTxHash() = txHash
    override fun getStateIndex() = stateIndex
    override fun toString() = "${txHash}:${stateIndex}"
}
