package net.corda.uniqueness.datamodel.impl

import net.corda.v5.ledger.common.transaction.TransactionSignature
import net.corda.v5.application.uniqueness.model.UniquenessCheckResponse
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult

class UniquenessCheckResponseImpl(
    override val result: UniquenessCheckResult,
    override val signature: TransactionSignature?
) : UniquenessCheckResponse