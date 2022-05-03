package net.corda.v5.ledger.services

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.contracts.AttachmentResolutionException
import net.corda.v5.ledger.contracts.TransactionResolutionException
import net.corda.v5.ledger.contracts.TransactionVerificationException
import net.corda.v5.ledger.transactions.SignaturesMissingException
import net.corda.v5.ledger.transactions.SignedTransaction
import net.corda.v5.ledger.transactions.TransactionWithSignatures
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * Provides a transaction verification service.
 */
@DoNotImplement
interface TransactionVerificationService {

    /**
     * Verifies a [SignedTransaction].
     *
     * This includes:
     *
     * - Resolving the transaction's inputs and attachments from the local storage and performing full transaction verification, including
     * running the contracts.
     * - Checking the transaction's signatures are valid if [checkSufficientSignatures] is set to true, including checking that all the
     * required signatures are present.
     *
     * @throws AttachmentResolutionException If a required attachment was not found in storage.
     * @throws TransactionResolutionException If an input points to a transaction not found in storage.
     * @throws TransactionVerificationException If the transaction does not successfully execute verification of contracts contained within
     * it.
     * @throws SignatureException If any signatures were invalid or unrecognised.
     * @throws SignaturesMissingException If any signatures that should have been present are missing.
     * @throws InvalidKeyException If the key on a signature is invalid.
     */
    @Throws(SignatureException::class, InvalidKeyException::class)
    fun verify(transaction: SignedTransaction, checkSufficientSignatures: Boolean)

    /**
     * Verifies a [SignedTransaction].
     *
     * This includes:
     *
     * - Resolving the transaction's inputs and attachments from the local storage and performing full transaction verification, including
     * running the contracts.
     * - Checking the transaction's signatures are valid, including checking that all the required signatures are present.
     *
     * @throws AttachmentResolutionException If a required attachment was not found in storage.
     * @throws TransactionResolutionException If an input points to a transaction not found in storage.
     * @throws TransactionVerificationException If the transaction does not successfully execute verification of contracts contained within
     * it.
     * @throws SignatureException If any signatures were invalid or unrecognised.
     * @throws SignaturesMissingException If any signatures that should have been present are missing.
     * @throws InvalidKeyException If the key on a signature is invalid.
     */
    @Throws(SignatureException::class, InvalidKeyException::class)
    fun verify(transaction: SignedTransaction)


    /**
     * Verifies the signatures on the input [transaction] and throws if any are missing. In this context, "verifying" means checking they
     * are valid signatures and that their public keys are in the [TransactionWithSignatures.requiredSigningKeys] set.
     *
     * @param transaction The [TransactionWithSignatures] that will have its signatures verified.
     *
     * @throws SignatureException If any signatures are invalid or unrecognised.
     * @throws SignaturesMissingException If any signatures should have been present but were not.
     * @throws InvalidKeyException If the key on a signature is invalid.
     */
    @Throws(SignatureException::class, InvalidKeyException::class)
    fun verifyRequiredSignatures(transaction: TransactionWithSignatures)

    /**
     * Verifies the signatures on the input [transaction] and throws if any are missing which aren't passed as parameters. In this context,
     * "verifying" means checking they are valid signatures and that their public keys are in the
     * [TransactionWithSignatures.requiredSigningKeys] set.
     *
     * Normally you would not provide any keys to this function, but if you're in the process of building a partial transaction and you want
     * to access the contents before you've signed it, you can specify your own keys here to bypass that check.
     *
     * @param transaction The [TransactionWithSignatures] that will have its signatures verified.
     * @param allowedToBeMissing The [PublicKey]s that can be missing when verifying the [transaction]'s signatures.
     *
     * @throws SignatureException If any signatures are invalid or unrecognised.
     * @throws SignaturesMissingException If any signatures should have been present but were not.
     * @throws InvalidKeyException If the key on a signature is invalid.
     */
    @Throws(SignatureException::class, InvalidKeyException::class)
    fun verifySignaturesExcept(transaction: TransactionWithSignatures, vararg allowedToBeMissing: PublicKey)

    /**
     * Verifies the signatures on the input [transaction] and throws if any are missing which aren't passed as parameters. In this context,
     * "verifying" means checking they are valid signatures and that their public keys are in the
     * [TransactionWithSignatures.requiredSigningKeys] set.
     *
     * Normally you would not provide any keys to this function, but if you're in the process of building a partial transaction and you want
     * to access the contents before you've signed it, you can specify your own keys here to bypass that check.
     *
     * @param transaction The [TransactionWithSignatures] that will have its signatures verified.
     * @param allowedToBeMissing The [PublicKey]s that can be missing when verifying the [transaction]'s signatures.
     *
     * @throws SignatureException If any signatures are invalid or unrecognised.
     * @throws SignaturesMissingException If any signatures should have been present but were not.
     * @throws InvalidKeyException If the key on a signature is invalid.
     */
    @Throws(SignatureException::class, InvalidKeyException::class)
    fun verifySignaturesExcept(transaction: TransactionWithSignatures, allowedToBeMissing: Collection<PublicKey>)
}
