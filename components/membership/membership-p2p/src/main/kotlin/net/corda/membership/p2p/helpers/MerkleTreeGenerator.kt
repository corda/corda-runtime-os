package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.toAvro
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.MemberInfo
import org.slf4j.LoggerFactory

class MerkleTreeGenerator(
    private val merkleTreeProvider: MerkleTreeProvider,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val NODE_HASH_PREFIX = "CORDA_MEMBERSHIP_NODE"
        const val LEAF_HASH_PREFIX = "CORDA_MEMBERSHIP_LEAF"
    }
    private val hashDigestProvider = merkleTreeProvider.createHashDigestProvider(
        HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
        DigestAlgorithmName.SHA2_256,
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
        return createTree(leaves)
    }

    fun createTree(leaves: List<ByteArray>): MerkleTree = merkleTreeProvider.createTree(leaves, hashDigestProvider)
}
