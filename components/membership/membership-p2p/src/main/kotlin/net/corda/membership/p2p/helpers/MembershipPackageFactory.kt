package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.bytes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.utilities.time.Clock
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import java.nio.ByteBuffer
import net.corda.data.crypto.SecureHash as AvroSecureHash

@Suppress("LongParameterList")
class MembershipPackageFactory(
    private val clock: Clock,
    private val keyEncodingService: KeyEncodingService,
    private val type: DistributionType,
    private val merkleTreeGenerator: MerkleTreeGenerator,
    private val idFactory: () -> String,
) {
    private companion object {
        fun SecureHash.toAvro(): AvroSecureHash =
            AvroSecureHash(this.algorithm, ByteBuffer.wrap(bytes))
    }
    private fun DigitalSignatureWithKey.toAvro() =
        CryptoSignatureWithKey.newBuilder()
            .setBytes(ByteBuffer.wrap(this.bytes))
            .setPublicKey(ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(this.by)))
            .build()

    private fun SignatureSpec.toAvro() =
        CryptoSignatureSpec(this.signatureName, null, null)

    fun createMembershipPackage(
        mgmSigner: Signer,
        membersToSend: Collection<SelfSignedMemberInfo>,
        hashCheck: SecureHash,
        groupParameters: InternalGroupParameters,
    ): MembershipPackage {
        val mgmSignatureSpec = mgmSigner.signatureSpec.toAvro()
        val signedMembers = membersToSend.map {
            val memberTree = merkleTreeGenerator.generateTreeUsingSignedMembers(listOf(it))
            val mgmSignature = mgmSigner.sign(memberTree.root.bytes).toAvro()
            SignedMemberInfo.newBuilder()
                .setMemberContext(
                    SignedData(
                        ByteBuffer.wrap(it.memberContextBytes),
                        it.memberSignature,
                        it.memberSignatureSpec
                    )
                )
                .setMgmContext(
                    SignedData(
                        ByteBuffer.wrap(it.mgmContextBytes),
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
