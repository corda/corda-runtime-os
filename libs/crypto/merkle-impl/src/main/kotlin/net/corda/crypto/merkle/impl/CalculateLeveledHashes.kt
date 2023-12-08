package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.merkle.MerkleProof


@Suppress("unused")
fun calculateLeveledHashes(proof: MerkleProof): List<LeveledHash> {
    require(proof.leaves.isNotEmpty(), {"MerkleProof must have leaves"})
    require(proof.leaves.all { it.index >= 0 && it.index < proof.treeSize }, {"MerkleProof leaves cannot point outside of the original tree."})
    require(proof.leaves.map { it.index }.toSet().size == proof.leaves.size, {"MerkleProof leaves cannot have duplications."})
    return emptyList()
}
