package net.corda.v5.ledger.transactions

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.services.TransactionService
import java.security.PublicKey

/** An interface for transactions containing signatures, with logic for signature verification. */
@DoNotImplement
interface TransactionWithSignatures {
    /**
     * List of signatures on this transaction.
     *
     * @see TransactionService.verifyRequiredSignatures
     */
    val sigs: List<DigitalSignatureAndMetadata>

    /** Specifies all the public keys that require signatures for the transaction to be valid. */
    val requiredSigningKeys: Set<PublicKey>

    val id: SecureHash

    /**
     * Return the [PublicKey]s for which we still need signatures.
     */
    val missingSigningKeys: Set<PublicKey>
        get() {
            val sigKeys = sigs.map { it.by }.toSet()
            // TODO Problem is that we can get single PublicKey wrapped as CompositeKey in allowedToBeMissing/mustSign
            //  equals on CompositeKey won't catch this case (do we want to single PublicKey be equal to the same key wrapped in CompositeKey with threshold 1?)
            return requiredSigningKeys.filter { !it.isFulfilledBy(sigKeys) }.toSet()
        }

    /**
     * Get a human readable description of where signatures are required from, and are missing, to assist in debugging
     * the underlying cause.
     *
     * Note that the results should not be serialised, parsed or expected to remain stable between Corda versions.
     */
    fun getKeyDescriptions(keys: Set<PublicKey>): List<String>
}
