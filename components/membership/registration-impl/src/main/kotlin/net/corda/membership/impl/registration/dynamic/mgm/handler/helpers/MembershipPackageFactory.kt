package net.corda.membership.impl.registration.dynamic.mgm.handler.helpers

import net.corda.chunking.toAvro
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.holdingIdentity
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer

@Suppress("LongParameterList")
internal class MembershipPackageFactory(
    private val clock: Clock,
    hashingService: DigestService,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    private val type: DistributionType,
    private val merkleTreeFactory: MerkleTreeFactory = MerkleTreeFactory(
        cordaAvroSerializationFactory,
        hashingService,
    ),
    private val idFactory: () -> String,
) {
    private companion object {
        val logger = contextLogger()
    }
    private fun DigitalSignature.WithKey.toAvro() =
        CryptoSignatureWithKey.newBuilder()
            .setBytes(ByteBuffer.wrap(this.bytes))
            .setPublicKey(ByteBuffer.wrap(cipherSchemeMetadata.encodeAsByteArray(this.by)))
            .setContext(
                KeyValuePairList(
                    this.context.map { KeyValuePair(it.key, it.value) }
                )
            )
            .build()

    private val serializer: CordaAvroSerializer<LayeredPropertyMap> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer<LayeredPropertyMap> {
            logger.warn("Serialization failed")
        }
    }

    fun createMembershipPackage(
        mgmSigner: Signer,
        membersSignatures: Map<HoldingIdentity, CryptoSignatureWithKey>,
        membersToSend: Collection<MemberInfo>,
    ): MembershipPackage {
        val signedMembers = membersToSend.map {
            val memberTree = merkleTreeFactory.buildTree(listOf(it))
            val mgmSignature = mgmSigner.sign(memberTree.root.bytes).toAvro()
            val memberSignature = membersSignatures[it.holdingIdentity]
                ?: throw CordaRuntimeException("Could not find member signature for ${it.name}")
            SignedMemberInfo.newBuilder()
                .setMemberContext(ByteBuffer.wrap(serializer.serialize(it.memberProvidedContext)))
                .setMgmContext(ByteBuffer.wrap(serializer.serialize(it.mgmProvidedContext)))
                .setMemberSignature(memberSignature)
                .setMgmSignature(mgmSignature)
                .build()
        }
        val membersTree = merkleTreeFactory.buildTree(membersToSend)
        val membership = SignedMemberships.newBuilder()
            .setMemberships(signedMembers)
            .setHashCheck(membersTree.root.toAvro())
            .build()
        return MembershipPackage.newBuilder()
            .setDistributionType(type)
            .setCurrentPage(0)
            .setPageCount(1)
            .setCpiWhitelist(null)
            .setGroupParameters(null)
            .setMemberships(
                membership
            )
            .setDistributionMetaData(
                DistributionMetaData(
                    idFactory(),
                    clock.instant(),
                )
            )
            .build()
    }
}
