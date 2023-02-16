package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import java.time.Instant

data class UniquenessCheckResultSuccessImpl(
    private val resultTimestamp: Instant
) : UniquenessCheckResultSuccess {
    override fun getResultTimestamp() = resultTimestamp
}

data class UniquenessCheckResultFailureImpl(
    private val resultTimestamp: Instant,
    private val error: UniquenessCheckError
) : UniquenessCheckResultFailure {
    override fun getResultTimestamp() = resultTimestamp
    override fun getError() = error
}
