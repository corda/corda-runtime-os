package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResponse
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResult

class UniquenessCheckResponseImpl(
    override val result: UniquenessCheckResult,
    override val signature: DigitalSignatureAndMetadata?
) : UniquenessCheckResponse