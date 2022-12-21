package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckResponse

class LedgerUniquenessCheckResponseImpl(
    override val result: UniquenessCheckResult,
    override val signature: DigitalSignatureAndMetadata?
) : LedgerUniquenessCheckResponse