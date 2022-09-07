package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash

data class UniquenessCheckStateRefImpl(
    override val txHash: SecureHash,
    override val stateIndex: Int
) : UniquenessCheckStateRef