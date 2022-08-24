package net.corda.membership.p2p.helpers

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.toAvro
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.membership.MemberInfo

class MerkleTreeGenerator(
    private val merkleTreeFactory: MerkleTreeFactory,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    private companion object {
        val logger = contextLogger()
        const val NODE_HASH_PREFIX = "CORDA_MEMBERSHIP_NODE"
        const val LEAF_HASH_PREFIX = "CORDA_MEMBERSHIP_LEAF"
    }
    private val hashDigestProvider = merkleTreeFactory.createHashDigestProvider(
        HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
        DigestAlgorithmName.DEFAULT_ALGORITHM_NAME,
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to LEAF_HASH_PREFIX.toByteArray(),
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to NODE_HASH_PREFIX.toByteArray(),
        )
    )
    private val serializer: CordaAvroSerializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
            logger.warn("Serialization failed")
        }
    }

    fun generateTree(members: Collection<MemberInfo>): MerkleTree {
        val leaves = members
            .sortedBy { member ->
                member.name
            }.flatMap { member ->
                listOf(
                    serializer.serialize(member.memberProvidedContext.toAvro()),
                    serializer.serialize(member.mgmProvidedContext.toAvro()),
                )
            }.filterNotNull()
        return merkleTreeFactory.createTree(leaves, hashDigestProvider)
    }
}
