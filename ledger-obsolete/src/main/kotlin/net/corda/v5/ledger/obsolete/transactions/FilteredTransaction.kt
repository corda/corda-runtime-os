package net.corda.v5.ledger.obsolete.transactions

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.obsolete.contracts.Command
import net.corda.v5.ledger.obsolete.contracts.ComponentGroupEnum
import net.corda.v5.ledger.obsolete.contracts.StateRef
import net.corda.v5.ledger.obsolete.merkle.PartialMerkleTree
import java.security.PublicKey

/**
 * Class representing merkleized filtered transaction.
 */
@DoNotImplement
interface FilteredTransaction : TraversableTransaction {

    val filteredComponentGroups: List<FilteredComponentGroup>
    val groupHashes: List<SecureHash>

    /**
     * Runs verification of partial Merkle branch against [id].
     * Note that empty filtered transactions (with no component groups) are accepted as well,
     * e.g. for Timestamp Authorities to blindly sign or any other similar case in the future
     * that requires a blind signature over a transaction's [id].
     * @throws FilteredTransactionVerificationException if verification fails.
     */
    @Throws(FilteredTransactionVerificationException::class)
    fun verify()

    /**
     * Function that checks the whole filtered structure.
     * Force type checking on a structure that we obtained, so we don't sign more than expected.
     * Example: Oracle is implemented to check only for commands, if it gets an attachment and doesn't expect it - it can sign
     * over a transaction with the attachment that wasn't verified. Of course it depends on how you implement it, but else -> false
     * should solve a problem with possible later extensions to WireTransaction.
     * @param checkingFun function that performs type checking on the structure fields and provides verification logic accordingly.
     * @return false if no elements were matched on a structure or checkingFun returned false.
     */
    fun checkWithFun(checkingFun: (Any) -> Boolean): Boolean

    /**
     * Function that checks if all of the components in a particular group are visible.
     * This functionality is required on non-Validating Notaries to check that all inputs are visible.
     * It might also be applied in Oracles or any other entity requiring [Command] visibility, but because this method
     * cannot distinguish between related and unrelated to the signer [Command]s, one should use the
     * [checkCommandVisibility] method, which is specifically designed for [Command] visibility purposes.
     * The logic behind this algorithm is that we check that the root of the provided group partialMerkleTree matches with the
     * root of a fullMerkleTree if computed using all visible components.
     * Note that this method is usually called after or before [verify], to also ensure that the provided partial Merkle
     * tree corresponds to the correct leaf in the top Merkle tree.
     * @param componentGroupEnum the [ComponentGroupEnum] that corresponds to the componentGroup for which we require full component visibility.
     * @throws ComponentVisibilityException if not all of the components are visible or if the component group is not present in the [FilteredTransaction].
     */
    @Throws(ComponentVisibilityException::class)
    fun checkAllComponentsVisible(componentGroupEnum: ComponentGroupEnum)

    /**
     * Function that checks if all of the commands that should be signed by the input public key are visible.
     * This functionality is required from Oracles to check that all of the commands they should sign are visible.
     * This algorithm uses the [ComponentGroupEnum.SIGNERS_GROUP] to count how many commands should be signed by the
     * input [PublicKey] and it then matches it with the size of received [commands].
     * Note that this method does not throw if there are no commands for this key to sign in the original [WireTransaction].
     * @param publicKey signer's [PublicKey]
     * @throws ComponentVisibilityException if not all of the related commands are visible.
     */
    @Throws(ComponentVisibilityException::class)
    fun checkCommandVisibility(publicKey: PublicKey)
}

/**
 * A FilteredComponentGroup is used to store the filtered list of transaction components of the same type in serialised form.
 * This is similar to [ComponentGroup], but it also includes the corresponding nonce per component.
 */
@CordaSerializable
data class FilteredComponentGroup(
    override val groupIndex: Int,
    override val components: List<OpaqueBytes>,
    val nonces: List<SecureHash>,
    val partialMerkleTree: PartialMerkleTree
) : ComponentGroup(groupIndex, components) {
    init {
        check(components.size == nonces.size) { "Size of transaction components and nonces do not match" }
    }
}

/** Thrown when checking for visibility of all-components in a group in [FilteredTransaction.checkAllComponentsVisible].
 * @param id transaction's id.
 * @param reason information about the exception.
 */
@CordaSerializable
class ComponentVisibilityException(val id: SecureHash, val reason: String) : CordaRuntimeException("Component visibility error for transaction with id:$id. Reason: $reason")

/** Thrown when [FilteredTransaction.verify] fails.
 * @param id transaction's id.
 * @param reason information about the exception.
 */
@CordaSerializable
class FilteredTransactionVerificationException(val id: SecureHash, val reason: String) : CordaRuntimeException("Transaction with id:$id cannot be verified. Reason: $reason")

/** Wrapper over [StateRef] to be used when filtering reference states. */
@CordaSerializable
data class ReferenceStateRef(val stateRef: StateRef)

/** Wrapper over [SecureHash] to be used when filtering group parameters hash. */
@CordaSerializable
data class GroupParametersHash(val hash: SecureHash)
