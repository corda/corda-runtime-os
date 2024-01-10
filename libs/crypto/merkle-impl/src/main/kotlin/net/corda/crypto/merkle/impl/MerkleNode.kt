package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.SecureHash

/**
 * A MerkleNode represents a node on a tree
 *
 * @param index the position of the node at its level, counting from the left (which is zero)
 * @param hash the hash of the node, which is a hash of its children
 */

data class MerkleNode(val index: Int, val hash: SecureHash)
