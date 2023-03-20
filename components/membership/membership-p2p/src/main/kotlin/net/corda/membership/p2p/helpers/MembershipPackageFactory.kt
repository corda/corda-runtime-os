package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.toAvro
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.data.membership.p2p.WireGroupParameters
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

@Suppress("LongParameterList")
class MembershipPackageFactory(
    private val clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val keyEncodingService: KeyEncodingService,
    private val type: DistributionType,
    private val merkleTreeGenerator: MerkleTreeGenerator,
    private val idFactory: () -> String,
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    private fun DigitalSignature.WithKey.toAvro() =
        CryptoSignatureWithKey.newBuilder()
            .setBytes(ByteBuffer.wrap(this.bytes))
            .setPublicKey(ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(this.by)))
            .build()

    private fun SignatureSpec.toAvro() =
        CryptoSignatureSpec(this.signatureName, null, null)

    private val serializer: CordaAvroSerializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
            logger.warn("Serialization failed")
        }
    }

    fun createMembershipPackage(
        mgmSigner: Signer,
        membersSignatures: Map<HoldingIdentity, Pair<CryptoSignatureWithKey, CryptoSignatureSpec>>,
        membersToSend: Collection<MemberInfo>,
        hashCheck: SecureHash,
        groupParameters: GroupParameters,
    ): MembershipPackage {
        val mgmSignatureSpec = mgmSigner.signatureSpec.toAvro()
        val signedMembers = membersToSend.map {
            val memberTree = merkleTreeGenerator.generateTree(listOf(it))
            val mgmSignature = mgmSigner.sign(memberTree.root.bytes).toAvro()
            val (memberSignature, memberSignatureSpec) =
                membersSignatures[it.holdingIdentity]?.let { signatureAndSpec ->
                    signatureAndSpec.first to signatureAndSpec.second
                } ?: throw CordaRuntimeException("Could not find member signature and signature spec for ${it.name}")

            SignedMemberInfo.newBuilder()
                .setMemberContext(ByteBuffer.wrap(serializer.serialize(it.memberProvidedContext.toAvro())))
                .setMgmContext(ByteBuffer.wrap(serializer.serialize(it.mgmProvidedContext.toAvro())))
                .setMemberSignature(memberSignature)
                .setMemberSignatureSpec(memberSignatureSpec)
                .setMgmSignature(mgmSignature)
                .setMgmSignatureSpec(mgmSignatureSpec)
                .build()
        }
        val wireGroupParameters = serializer.serialize(groupParameters.toAvro())?.let {
            WireGroupParameters(
                ByteBuffer.wrap(it),
                mgmSigner.sign(it).toAvro(),
                mgmSigner.signatureSpec.toAvro()
            )
        } ?: throw CordaRuntimeException("Failed to serialize group parameters.")
        val membership = SignedMemberships.newBuilder()
            .setMemberships(signedMembers)
            .setHashCheck(hashCheck.toAvro())
            .build()
        return MembershipPackage.newBuilder()
            .setDistributionType(type)
            .setCurrentPage(0)
            .setPageCount(1)
            .setCpiAllowList(null)
            .setGroupParameters(wireGroupParameters)
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
