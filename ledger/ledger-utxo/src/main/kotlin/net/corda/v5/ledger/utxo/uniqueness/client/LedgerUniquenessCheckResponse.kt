package net.corda.v5.ledger.utxo.uniqueness.client

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult

/**
 * This is a response class that wraps the result of the uniqueness checking and a potential signature,
 * if the request was processed successfully.
 *
 * @property result The result produced by the uniqueness checker
 * @property signature Contains the signature provided by the uniqueness checker. Will be `null` if the
 * result is a failure.
 */
interface LedgerUniquenessCheckResponse {
    val result: UniquenessCheckResult
    val signature: DigitalSignatureAndMetadata?
}
