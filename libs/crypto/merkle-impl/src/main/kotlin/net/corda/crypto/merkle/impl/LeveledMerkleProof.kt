package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf

@Suppress("Unused")
class LeveledMerkleProof(
     private val loadedLeaves: List<IndexedMerkleLeaf>,
     private val loadedHashes: List<LeveledHash>,
     private val treeSize: Int,
     private val digest: MerkleTreeHashDigestProvider
)