package net.corda.membership.p2p.helpers

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant

class MembershipPackageFactoryTest {
    private val clock = TestClock(Instant.ofEpochMilli(100))
    private val groupParametersBytes = "test-group-parameters".toByteArray()
    private val groupParameters: InternalGroupParameters = mock {
        on { groupParameters } doReturn groupParametersBytes
    }
    private val pubKey: PublicKey = mock {
        on { encoded } doReturn "test-key".toByteArray()
    }
    private val signedGroupParameters: DigitalSignatureWithKey = mock {
        on { bytes } doReturn "dummy-signature".toByteArray()
        on { by } doReturn pubKey
    }
    private val serializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(eq(groupParameters.toAvro())) } doReturn groupParametersBytes
    }
    private val cipherSchemeMetadata = mock<CipherSchemeMetadata> {
        on { encodeAsByteArray(any()) } doAnswer {
            val pk = it.arguments[0] as PublicKey
            pk.encoded
        }
    }
    private val merkleTreeGenerator = mock<MerkleTreeGenerator>()
    private val mgmSigner = mock<Signer> {
        on { sign(eq(groupParametersBytes)) } doReturn signedGroupParameters
        on { this.signatureSpec } doReturn SignatureSpecImpl("dummy")
    }
    private val signatureSpec = CryptoSignatureSpec("dummy", null, null)
    private val membersCount = 4
    private val members = (1..membersCount).map {
        mockSignedMemberInfo("name-$it", it)
    }.also { members ->
        members.associateWith { member ->
            val hashBytes = "root-${member.name}".toByteArray()
            val alg = "alg-${member.name}"
            val treeRoot = SecureHashImpl(alg, hashBytes)
            mock<MerkleTree> {
                on { root } doReturn treeRoot
            }
        }.onEach {
            whenever(merkleTreeGenerator.generateTreeUsingSignedMembers(listOf(it.key))).doReturn(it.value)
        }.onEach { entry ->
            val hash = entry.value.root.bytes
            val bytes = "bytes-${entry.key.name}".toByteArray()
            val pk = "pk-${entry.key.name}".toByteArray()
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn pk
            }
            val signature = DigitalSignatureWithKey(
                publicKey,
                bytes
            )
            whenever(mgmSigner.sign(hash)).thenReturn(signature)
        }
    }
    private val allAlg = "all-alg"
    private val checkHash = SecureHashImpl(allAlg, "all".toByteArray())

    private val factory = MembershipPackageFactory(
        clock,
        cipherSchemeMetadata,
        DistributionType.STANDARD,
        merkleTreeGenerator
    ) { "id" }

    @Test
    fun `createMembershipPackage create the correct membership package meta data`() {
        val membershipPackage = factory.createMembershipPackage(
            mgmSigner,
            members,
            checkHash,
            groupParameters,
        )

        assertSoftly {
            it.assertThat(membershipPackage.distributionType).isEqualTo(DistributionType.STANDARD)
            it.assertThat(membershipPackage.currentPage).isEqualTo(0)
            it.assertThat(membershipPackage.pageCount).isEqualTo(1)
            it.assertThat(membershipPackage.distributionMetaData).isEqualTo(
                DistributionMetaData(
                    "id",
                    clock.instant(),
                )
            )
            with(membershipPackage.groupParameters) {
                it.assertThat(this.groupParameters).isEqualTo(ByteBuffer.wrap(groupParametersBytes))
                it.assertThat(this.mgmSignature).isEqualTo(
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(pubKey.encoded),
                        ByteBuffer.wrap(signedGroupParameters.bytes)
                    )
                )
            }
        }
    }

    @Test
    fun `createMembershipPackage create the correct membership hash check`() {
        val hashCheck = factory.createMembershipPackage(
            mgmSigner,
            members,
            checkHash,
            groupParameters,
        ).memberships.hashCheck

        assertSoftly {
            it.assertThat(hashCheck.algorithm).isEqualTo(allAlg)
            it.assertThat(hashCheck.bytes).isEqualTo(ByteBuffer.wrap("all".toByteArray()))
        }
    }

    @Test
    fun `createMembershipPackage create the correct memberships`() {
        val memberships = factory.createMembershipPackage(
            mgmSigner,
            members,
            checkHash,
            groupParameters,
        ).memberships.memberships

        val expectedMembers = (1..membersCount).map { index ->
            val memberSignature = CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-O=name-$index, L=London, C=GB".toByteArray()),
                ByteBuffer.wrap("sig-O=name-$index, L=London, C=GB".toByteArray())
            )
            val mgmSignature = CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-O=name-$index, L=London, C=GB".toByteArray()),
                ByteBuffer.wrap("bytes-O=name-$index, L=London, C=GB".toByteArray())
            )

            SignedMemberInfo(
                SignedData(
                    ByteBuffer.wrap("memberContext-name-$index".toByteArray()),
                    memberSignature,
                    signatureSpec
                ),
                SignedData(
                    ByteBuffer.wrap("mgmContext-name-$index".toByteArray()),
                    mgmSignature,
                    signatureSpec
                )
            )
        }
        assertThat(memberships)
            .containsExactlyInAnyOrderElementsOf(
                expectedMembers
            )
    }

    private fun mockSignedMemberInfo(
        memberName: String,
        index: Int,
    ): SelfSignedMemberInfo {
        val mgmContext = mock<MGMContext>()
        whenever(mgmContext.entries).thenReturn(
            mapOf("mgmContext" to "mgm+$memberName").entries
        )
        whenever(serializer.serialize(mgmContext.toAvro())).doReturn("mgmContext-$memberName".toByteArray())
        val memberContext = mock<MemberContext> {
            on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn "GroupId"
        }
        whenever(memberContext.entries).thenReturn(
            mapOf("memberContext" to "member+$memberName").entries
        )
        whenever(serializer.serialize(memberContext.toAvro())).doReturn("memberContext-$memberName".toByteArray())
        val x500Name = MemberX500Name.parse("C=GB,L=London,O=$memberName")
        return mock {
            on { memberContextBytes } doReturn "memberContext-name-$index".toByteArray()
            on { mgmContextBytes } doReturn "mgmContext-name-$index".toByteArray()
            on { mgmProvidedContext } doReturn mgmContext
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn x500Name
            on { memberSignature } doReturn CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-$x500Name".toByteArray()),
                ByteBuffer.wrap("sig-$x500Name".toByteArray())
            )
            on { memberSignatureSpec } doReturn signatureSpec
        }
    }
}
