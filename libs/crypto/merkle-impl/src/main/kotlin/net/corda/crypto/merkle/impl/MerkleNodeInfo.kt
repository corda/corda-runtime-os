package net.corda.crypto.merkle.impl

/**
 * Represents additional information about a node
 *
 * @param node The node itself
 * @param level The level within the Merkle tree, counting from the top with 0 being the top
 * @param consumed If set, the index in the proof hashes used for this node. If unset, the node is calculated.
 */
internal data class MerkleNodeInfo(val node: MerkleNode, val level: Int, val consumed: Int?)
