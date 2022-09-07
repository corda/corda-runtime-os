package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash

data class UniquenessCheckStateDetailsImpl(
    override val stateRef: UniquenessCheckStateRef,
    override val consumingTxId: SecureHash?
) : UniquenessCheckStateDetails