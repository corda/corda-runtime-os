package net.corda.membership.impl.registration.dynamic.mgm.handler.helpers

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.membership.lib.impl.MemberInfoExtension
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.holdingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val hashingService = mock<DigestService>()
    private val serializer = mock<CordaAvroSerializer<LayeredPropertyMap>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<LayeredPropertyMap>(any()) } doReturn serializer
    }
    private val cipherSchemeMetadata = mock<CipherSchemeMetadata> {
        on { encodeAsByteArray(any()) } doAnswer {
            val pk = it.arguments[0] as PublicKey
            pk.encoded
        }
    }
    private val merkleTreeFactory = mock<MerkleTreeFactory>()
    private val mgmSigner = mock<Signer>()
    private val membersCount = 4
    private val members = (1..membersCount).map {
        mockMemberInfo("name-$it")
    }.also { members ->
        members.associateWith { member ->
            val hashBytes = "root-${member.name}".toByteArray()
            val alg = "alg-${member.name}"
            val treeRoot = mock<SecureHash> {
                on { bytes } doReturn hashBytes
                on { algorithm } doReturn alg
            }
            mock<MerkleTree> {
                on { root } doReturn treeRoot
            }
        }.onEach {
            whenever(merkleTreeFactory.buildTree(listOf(it.key))).doReturn(it.value)
        }.onEach { entry ->
            val hash = entry.value.root.bytes
            val bytes = "bytes-${entry.key.name}".toByteArray()
            val pk = "pk-${entry.key.name}".toByteArray()
            val publicKey = mock<PublicKey> {
                on { encoded } doReturn pk
            }
            val signature = DigitalSignature.WithKey(
                publicKey,
                bytes,
                mapOf("name" to entry.key.name.toString())
            )
            whenever(mgmSigner.sign(hash)).thenReturn(signature)
        }
    }
    private val signature = members.associate {
        it.holdingIdentity to CryptoSignatureWithKey(
            ByteBuffer.wrap("pk-${it.name}".toByteArray()),
            ByteBuffer.wrap("sig-${it.name}".toByteArray()),
            KeyValuePairList(
                listOf(
                    KeyValuePair("name", it.name.toString())
                )
            ),
        )
    }
    private val allAlg = "all-alg".also { allAlg ->
        val allRoot = mock<SecureHash> {
            on { bytes } doReturn "all".toByteArray()
            on { algorithm } doReturn allAlg
        }
        mock<MerkleTree> {
            on { root } doReturn allRoot
        }.also {
            whenever(merkleTreeFactory.buildTree(members)).doReturn(it)
        }
        DigitalSignature.WithKey(
            mock {
                on { encoded } doReturn "pk-all".toByteArray()
            },
            "all-sig".toByteArray(),
            mapOf("name" to "all")
        ).also {
            whenever(mgmSigner.sign(allRoot.bytes)).doReturn(it)
        }
    }

    private val factory = MembershipPackageFactory(
        clock,
        hashingService,
        cordaAvroSerializationFactory,
        cipherSchemeMetadata,
        DistributionType.STANDARD,
        merkleTreeFactory
    ) { "id" }

    @Test
    fun `createMembershipPackage create the correct membership package meta data`() {
        val membershipPackage = factory.createMembershipPackage(
            mgmSigner,
            signature,
            members,
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
            it.assertThat(membershipPackage.cpiWhitelist).isNull()
            it.assertThat(membershipPackage.groupParameters).isNull()
        }
    }

    @Test
    fun `createMembershipPackage create the correct membership hash check`() {
        val hashCheck = factory.createMembershipPackage(
            mgmSigner,
            signature,
            members,
        ).memberships.hashCheck

        assertSoftly {
            it.assertThat(hashCheck.algorithm).isEqualTo(allAlg)
            it.assertThat(hashCheck.serverHash).isEqualTo(ByteBuffer.wrap("all".toByteArray()))
        }
    }

    @Test
    fun `createMembershipPackage create the correct memberships`() {
        val memberships = factory.createMembershipPackage(
            mgmSigner,
            signature,
            members,
        ).memberships.memberships

        val expectedMembers = (1..membersCount).map { index ->
            val memberSignature = CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-O=name-$index, L=London, C=GB".toByteArray()),
                ByteBuffer.wrap("sig-O=name-$index, L=London, C=GB".toByteArray()),
                KeyValuePairList(
                    listOf(
                        KeyValuePair("name", "O=name-$index, L=London, C=GB")
                    )
                )
            )
            val mgmSignature = CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-O=name-$index, L=London, C=GB".toByteArray()),
                ByteBuffer.wrap("bytes-O=name-$index, L=London, C=GB".toByteArray()),
                KeyValuePairList(
                    listOf(
                        KeyValuePair("name", "O=name-$index, L=London, C=GB")
                    )
                )
            )
            SignedMemberInfo(
                ByteBuffer.wrap("memberContext-name-$index".toByteArray()),
                ByteBuffer.wrap("mgmContext-name-$index".toByteArray()),
                memberSignature,
                mgmSignature
            )
        }
        assertThat(memberships)
            .containsExactlyInAnyOrderElementsOf(
                expectedMembers
            )
    }
    @Test
    fun `createMembershipPackage throws exception for missing signature`() {
        assertThrows<CordaRuntimeException> {
            factory.createMembershipPackage(
                mgmSigner,
                signature.minus(members.last().holdingIdentity),
                members,
            )
        }
    }

    private fun mockMemberInfo(
        memberName: String,
    ): MemberInfo {
        val mgmContext = mock<MGMContext>()
        whenever(serializer.serialize(mgmContext)).doReturn("mgmContext-$memberName".toByteArray())
        val memberContext = mock<MemberContext> {
            on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn "GroupId"
        }
        whenever(serializer.serialize(memberContext)).doReturn("memberContext-$memberName".toByteArray())
        return mock {
            on { mgmProvidedContext } doReturn mgmContext
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn MemberX500Name.Companion.parse("C=GB,L=London,O=$memberName")
        }
    }
}
