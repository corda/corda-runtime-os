package net.corda.v5.ledger.transactions

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.crypto.TransactionDigestAlgorithmNames
import net.corda.v5.ledger.identity.Party
import net.corda.v5.membership.GroupParameters

/**
 * A special transaction for changing the notary of a state. It only needs specifying the state(s) as input(s),
 * old and new notaries. Output states can be computed by applying the notary modification to corresponding inputs
 * on the fly.
 *
 * Contains all of the transaction components in serialized form.
 * This is used for calculating the transaction id in a deterministic fashion, since re-serializing properties
 * may result in a different byte sequence depending on the serialization context.
 */
@DoNotImplement
interface NotaryChangeWireTransaction : CoreTransaction {

    /** Identity of the notary service to reassign the states to.*/
    val newNotary: Party

    val serializedComponents: List<OpaqueBytes>

    val transactionDigestAlgorithmNames: TransactionDigestAlgorithmNames
}

/**
 * A notary change transaction with fully resolved inputs and signatures. In contrast with a regular transaction,
 * signatures are checked against the signers specified by input states' *participants* fields, so full resolution is
 * needed for signature verification.
 */
@DoNotImplement
interface NotaryChangeLedgerTransaction : FullTransaction, TransactionWithSignatures {

    val newNotary: Party

    operator fun component1(): List<StateAndRef<ContractState>>
    operator fun component2(): Party
    operator fun component3(): Party
    operator fun component4(): SecureHash
    operator fun component5(): List<DigitalSignatureAndMetadata>
    operator fun component6(): GroupParameters?
}
