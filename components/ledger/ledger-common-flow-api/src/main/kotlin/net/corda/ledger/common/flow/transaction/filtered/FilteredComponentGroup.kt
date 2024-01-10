package net.corda.ledger.common.flow.transaction.filtered

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * A filtered component group.
 * 
 * @property componentGroupIndex The index of the component group this [FilteredComponentGroup] represents.
 * @property merkleProof The [MerkleProof] calculated from and containing the filtered components.
 */
@CordaSerializable
data class FilteredComponentGroup(val componentGroupIndex: Int, val merkleProof: MerkleProof)