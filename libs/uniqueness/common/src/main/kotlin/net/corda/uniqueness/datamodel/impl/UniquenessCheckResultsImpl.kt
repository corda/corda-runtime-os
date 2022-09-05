package net.corda.uniqueness.datamodel.impl

import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckError
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResultSuccess
import java.time.Instant

data class UniquenessCheckResultSuccessImpl(
    override val resultTimestamp: Instant
) : UniquenessCheckResultSuccess

data class UniquenessCheckResultFailureImpl(
    override val resultTimestamp: Instant,
    override val error: UniquenessCheckError
) : UniquenessCheckResultFailure