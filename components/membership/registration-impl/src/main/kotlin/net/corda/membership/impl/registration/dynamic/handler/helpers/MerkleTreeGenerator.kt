package net.corda.membership.impl.registration.dynamic.handler.helpers

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.toAvro
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.membership.MemberInfo

internal class MerkleTreeGenerator(
    private val merkleTreeFactory: MerkleTreeFactory,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    private companion object {
        val logger = contextLogger()
    }
    private val hashDigestProvider = merkleTreeFactory.createHashDigestProvider(
        HASH_DIGEST_PROVIDER_DEFAULT_NAME,
        DigestAlgorithmName.DEFAULT_ALGORITHM_NAME,
    )
    private val serializer: CordaAvroSerializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
            logger.warn("Serialization failed")
        }
    }

    fun generateTree(members: Collection<MemberInfo>): MerkleTree {
        val leavers = members
            .sortedBy { member ->
                member.name
            }.flatMap { member ->
                listOf(
                    serializer.serialize(member.memberProvidedContext.toAvro()),
                    serializer.serialize(member.mgmProvidedContext.toAvro()),
                )
            }.filterNotNull()
        return merkleTreeFactory.createTree(leavers, hashDigestProvider)
    }
}
