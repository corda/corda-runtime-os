package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.bytes
import net.corda.crypto.core.toAvro
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
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

    private fun DigitalSignatureWithKey.toAvro() =
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
        groupParameters: InternalGroupParameters,
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
                .setMemberContext(
                    SignedData(
                        ByteBuffer.wrap(serializer.serialize(it.memberProvidedContext.toAvro())),
                        memberSignature,
                        memberSignatureSpec
                    )
                )
                .setMgmContext(
                    SignedData(
                        ByteBuffer.wrap(serializer.serialize(it.mgmProvidedContext.toAvro())),
                        mgmSignature,
                        mgmSignatureSpec
                    )
                )
                .build()
        }
        val signedGroupParameters = SignedGroupParameters(
            ByteBuffer.wrap(groupParameters.groupParameters),
            mgmSigner.sign(groupParameters.groupParameters).toAvro(),
            mgmSigner.signatureSpec.toAvro()
        )
        val membership = SignedMemberships.newBuilder()
            .setMemberships(signedMembers)
            .setHashCheck(hashCheck.toAvro())
            .build()
        return MembershipPackage.newBuilder()
            .setDistributionType(type)
            .setCurrentPage(0)
            .setPageCount(1)
            .setGroupParameters(signedGroupParameters)
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

    fun createGroupParametersPackage(
        mgmSigner: Signer,
        groupParameters: InternalGroupParameters,
    ): MembershipPackage {
        val signedGroupParameters = SignedGroupParameters(
            ByteBuffer.wrap(groupParameters.groupParameters),
            mgmSigner.sign(groupParameters.groupParameters).toAvro(),
            mgmSigner.signatureSpec.toAvro()
        )
        return MembershipPackage.newBuilder()
            .setDistributionType(type)
            .setCurrentPage(0)
            .setPageCount(1)
            .setGroupParameters(signedGroupParameters)
            .setMemberships(null)
            .setDistributionMetaData(
                DistributionMetaData(
                    idFactory(),
                    clock.instant(),
                )
            )
            .build()
    }
}
