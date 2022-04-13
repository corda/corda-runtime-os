package net.corda.v5.ledger.services

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.ledger.transactions.FilteredTransaction
import net.corda.v5.ledger.transactions.LedgerTransaction
import net.corda.v5.ledger.transactions.SignedTransaction
import net.corda.v5.ledger.transactions.TransactionBuilder
import java.security.PrivateKey
import java.security.PublicKey

@DoNotImplement
@Suppress("TooManyFunctions")
interface TransactionService : TransactionStorage, TransactionMappingService, TransactionVerificationService,
    CordaServiceInjectable, CordaFlowInjectable {

    /**
     * Stores the given [SignedTransaction]s in the local transaction storage and then sends them to the vault for further processing if
     * [notifyVault] is true.
     *
     * This is expected to be run within a database transaction.
     *
     * @param notifyVault Indicates if the vault should be notified for the update.
     * @param first The [SignedTransaction] to record.
     * @param remaining Further [SignedTransaction]s to record.
     */
    fun record(notifyVault: Boolean, first: SignedTransaction, vararg remaining: SignedTransaction)

    /**
     * Stores the given [SignedTransaction]s in the local transaction storage and then sends them to the vault for further processing if
     * [notifyVault] is true.
     *
     * This is expected to be run within a database transaction.
     *
     * @param notifyVault Indicates if the vault should be notified for the update.
     * @param transactions The transactions to record.
     */
    fun record(notifyVault: Boolean, transactions: Collection<SignedTransaction>)

    /**
     * Stores the given [SignedTransaction]s in the local transaction storage and then sends them to the vault for further processing if
     * [statesToRecord] is not [StatesToRecord.NONE].
     *
     * This is expected to be run within a database transaction.
     *
     * @param statesToRecord How the vault should treat the output states of the transaction.
     * @param transactions The transactions to record.
     */
    fun record(statesToRecord: StatesToRecord, transactions: Collection<SignedTransaction>)

    /**
     * Stores the given [SignedTransaction]s in the local transaction storage and then sends them to the vault for further processing.
     *
     * This is expected to be run within a database transaction.
     *
     * @param first The [SignedTransaction] to record.
     * @param remaining Further [SignedTransaction]s to record.
     */
    fun record(first: SignedTransaction, vararg remaining: SignedTransaction)

    /**
     * Stores the given [SignedTransaction]s in the local transaction storage and then sends them to the vault for further processing.
     *
     * This is expected to be run within a database transaction.
     *
     * @param transactions The transactions to record.
     */
    fun record(transactions: Collection<SignedTransaction>)

    /**
     * Constructs an initial partially signed transaction from a [TransactionBuilder] using a set of keys all held in this node.
     *
     * @param builder The [TransactionBuilder] to seal with the node's signature. Any existing signatures on the builder will be preserved.
     * @param publicKeys A list of [PublicKey]s used to lookup the matching [PrivateKey] and sign.
     *
     * @return Returns a [SignedTransaction] with the new node signature attached.
     *
     * @throws IllegalArgumentException If an empty collection of keys are passed in.
     * @throws IllegalArgumentException If any keys are unavailable locally.
     */
    fun sign(builder: TransactionBuilder, publicKeys: Collection<PublicKey>): SignedTransaction

    /**
     * Constructs an initial partially signed transaction from a [TransactionBuilder] using keys stored inside the node. Signature metadata
     * is added automatically.
     *
     * @param builder The [TransactionBuilder] to seal with the node's signature. Any existing signatures on the builder will be preserved.
     *
     * @param publicKey The [PublicKey] matched to the internal [PrivateKey] to use in signing this transaction. If the passed in key is
     * actually a [CompositeKey], the code searches for the first child key hosted within this node to sign with.
     *
     * @return Returns a SignedTransaction with the new node signature attached.
     */
    fun sign(builder: TransactionBuilder, publicKey: PublicKey): SignedTransaction

    /**
     * Constructs an initial partially signed transaction from a [TransactionBuilder] using the default identity key contained in the node.
     * The legal identity key is used to sign.
     *
     * @param builder The [TransactionBuilder] to seal with the node's signature. Any existing signatures on the builder will be preserved.
     *
     * @return Returns a SignedTransaction with the new node signature attached.
     */
    fun sign(builder: TransactionBuilder): SignedTransaction

    /**
     * Appends an additional signature to an existing (partially) [SignedTransaction].
     *
     * @param signedTransaction The [SignedTransaction] to which the signature will be added.
     *
     * @param publicKey The [PublicKey] matching to a signing [java.security.PrivateKey] hosted in the node. If the [PublicKey] is actually
     * a [CompositeKey], the first leaf key found locally will be used for signing.
     *
     * @return A new [SignedTransaction] with the addition of the new signature.
     */
    fun sign(signedTransaction: SignedTransaction, publicKey: PublicKey): SignedTransaction

    /**
     * Appends an additional signature for an existing (partially) [SignedTransaction] using the default identity signing key of the node.
     *
     * @param signedTransaction The [SignedTransaction] to which the signature will be added.
     *
     * @return A new [SignedTransaction] with the addition of the new signature.
     */
    fun sign(signedTransaction: SignedTransaction): SignedTransaction

    /**
     * Creates an additional signature for an existing (partially) [SignedTransaction]. Additional [DigitalSignatureMetadata], including the
     * platform version used during signing and the cryptographic signature scheme use, is added to the signature.
     *
     * @param signedTransaction The [SignedTransaction] to which the signature will apply.
     *
     * @param publicKey The [PublicKey] matching to a signing [java.security.PrivateKey] hosted in the node. If the [PublicKey] is actually
     * a [CompositeKey] the first leaf key found locally will be used for signing.
     *
     * @return The [DigitalSignatureAndMetadata] generated by signing with the internally held [PrivateKey].
     */
    fun createSignature(signedTransaction: SignedTransaction, publicKey: PublicKey): DigitalSignatureAndMetadata

    /**
     * Creates a signature for an existing (partially) [SignedTransaction] using the default identity signing key of the node. The legal
     * identity key is used to sign. Additional [DigitalSignatureMetadata], including the platform version used during signing and the
     * cryptographic signature scheme use, is added to the signature.
     *
     * @param signedTransaction The [SignedTransaction] to which the signature will apply.
     *
     * @return The [DigitalSignatureAndMetadata] generated by signing with the internally held identity PrivateKey.
     */
    fun createSignature(signedTransaction: SignedTransaction): DigitalSignatureAndMetadata

    /**
     * Creates a signature for a FilteredTransaction. Additional [DigitalSignatureMetadata], including the platform version used during signing and
     * the cryptographic signature scheme use, is added to the signature.
     *
     * @param filteredTransaction the [FilteredTransaction] to which the signature will apply.
     *
     * @param publicKey The [PublicKey] matching to a signing [java.security.PrivateKey] hosted in the node. If the [PublicKey] is actually
     * a [CompositeKey], the first leaf key found locally will be used for signing.
     *
     * @return The [DigitalSignatureAndMetadata] generated by signing with the internally held [java.security.PrivateKey].
     */
    fun createSignature(filteredTransaction: FilteredTransaction, publicKey: PublicKey): DigitalSignatureAndMetadata

    /**
     * Creates a signature for a [FilteredTransaction] using the default identity signing key of the node. The legal identity key is used to
     * sign. Additional [DigitalSignatureMetadata], including the platform version used during signing and the cryptographic signature scheme use,
     * is added to the signature.
     *
     * @param filteredTransaction The [FilteredTransaction] to which the signature will apply.
     *
     * @return The [DigitalSignatureAndMetadata] generated by signing with the internally held identity [PrivateKey].
     */
    fun createSignature(filteredTransaction: FilteredTransaction): DigitalSignatureAndMetadata

    /**
     * Adds a note to an existing [LedgerTransaction] given by its unique [SecureHash] id. Multiple notes may be attached to the same
     * [LedgerTransaction]. These are additively and immutably persisted within the node local vault database in a single textual field
     * using a semi-colon separator.
     *
     * @param txId The transaction to add the note to.
     * @param noteText The text to add.
     */
    fun addNote(txId: SecureHash, noteText: String)

    /**
     * Get the notes for a transaction.
     *
     * @param txId The transaction to retrieve notes for.
     *
     * @return The notes related to the transaction.
     */
    fun getNotes(txId: SecureHash): Iterable<String>
}
