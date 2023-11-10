package net.corda.ledger.common.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata

// can we just make [WireTransaction.componentGroupLists] use an [ArrayList] instead of [List] so that it is indexed based?
// this removes the need for having a map? But none of the accessors also return the index which we'll need when storing.
// send proof and serialize on db side or serialize on flow side??
@CordaSerializable
class FilteredWireTransaction(
    // do i want the proof or the tree?
    // without the tree i don't have a secure hash
    // you can make a tree from a proof though
    private val id: SecureHash,
    val merkleProof: MerkleProof,
    val componentGroups: Map<Int, Map<Int, ByteArray>>,
    private val metadata: TransactionMetadata,
) : TransactionWithMetadata {

    init {
//        check((metadata as TransactionMetadataInternal).getNumberOfComponentGroups() == componentGroups.size) {
//            "Number of component groups in metadata structure description does not match with the real number!"
//        }
        check((metadata as TransactionMetadataInternal).getNumberOfComponentGroups() >= componentGroups.keys.max()) {
            "Number of component groups in metadata structure description does not match with the real number!"
        }
    }

    override fun getId(): SecureHash {
        return id
//        TODO("")
    }
    override fun getMetadata(): TransactionMetadata {
        return metadata
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FilteredWireTransaction

        if (merkleProof != other.merkleProof) return false
        if (componentGroups.size != other.componentGroups.size) return false

        return (other.componentGroups.all { i ->
            i.value.size == componentGroups[i.key]?.size &&
                    i.value.all { j ->
                        componentGroups[i.key]?.let { group -> group[j.key] contentEquals j.value } ?: false
                    }
        })
    }

    override fun hashCode(): Int {
        var result = merkleProof.hashCode()
        result = 31 * result + componentGroups.hashCode()
        return result
    }

    override fun toString(): String {
        return "FilteredWireTransaction(id=$id, " +
                "metadata=$metadata, " +
                "componentGroups=${
                    componentGroups.map { (groupIndex, group) ->
                        group.map { (componentIndex, component) -> 
                            "(groupIndex=$groupIndex, componentIndex=$componentIndex, size=${component.size}, sum=${component.sum()})" 
                        }
                    }
                })"
    }


}