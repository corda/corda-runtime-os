package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.SecureHash

/**
 * A MerkleNode represents a node on a tree, and is used as the parameter for callbacks when we walk
 * a proof of Merkle tree - see MerkleProofImpl.calculateRootInstrumented.
 *
 * @param indexWithinLevel The position of the node at its level, counting from the left (which is zero)
 * @param hash The hash of the node, which is a hash of its children
 */

internal data class MerkleNode(val indexWithinLevel: Int, val hash: SecureHash)
