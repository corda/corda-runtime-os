package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.merkle.MerkleProof


@Suppress("unused")
fun calculateLeveledHashes(proof: MerkleProof ): List<LeveledHash> {
    require(proof.leaves.isNotEmpty())
    return emptyList()
}
