package net.corda.ledger.common.data.transaction.filtered

import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import java.util.function.Predicate

/**
 * [ComponentGroupFilterParameters] is used with [FilteredTransactionFactory] to specify what component groups include in the
 * [FilteredTransaction] that the factory creates.
 *
 * @see FilteredTransactionFactory
 */
sealed interface ComponentGroupFilterParameters {

    /**
     * Gets the index of the component group to include.
     */
    val componentGroupIndex: Int

    /**
     * Gets the type of [MerkleProof] to create.
     */
    val merkleProofType: MerkleProofType

    /**
     * [AuditProof] includes a component group in the [FilteredTransaction] and creates an audit proof from the filtered components.
     *
     * @property componentGroupIndex The index of the component group to include.
     * @property deserializedClass The type that the component group deserializes its components into.
     * @property predicate Filtering function that is applied to each deserialized component with the group. A component is included in the
     * filtered component group when [predicate] returns `true` and is filtered out when `false` is returned.
     */
    data class AuditProof<T : Any>(
        override val componentGroupIndex: Int,
        val deserializedClass: Class<T>,
        val predicate: AuditProofPredicate<T>
    ) : ComponentGroupFilterParameters {
        override val merkleProofType = MerkleProofType.AUDIT

        sealed interface AuditProofPredicate<T> {
            /**
             *  [Content] include components in a [FilteredTransaction] where the components meet predicate.
             *
             *  @property predicate Filtering function that is applied to each deserialized component with the group
             */
            class Content<T>(private val predicate: Predicate<T>) : AuditProofPredicate<T>, Predicate<T> {
                override fun test(t: T): Boolean {
                    return predicate.test(t)
                }
            }

            /**
             *  [Index] indexes of components to include in a [FilteredTransaction]
             *
             *  @property indexes component indexes to include.
             */
            class Index<T>(private val indexes: List<Int>) : AuditProofPredicate<T>, Predicate<Int> {
                override fun test(t: Int): Boolean {
                    return t in indexes
                }
            }
        }
    }

    /**
     * [SizeProof] includes a component group in the [FilteredTransaction] and creates a size proof from the component group.
     *
     * @property componentGroupIndex The index of the component group to include.
     */
    data class SizeProof(override val componentGroupIndex: Int) : ComponentGroupFilterParameters {
        override val merkleProofType = MerkleProofType.SIZE
    }
}
