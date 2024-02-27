package net.corda.membership.p2p.helpers

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class MerkleTreeGeneratorTest {
    private val digestProvider = mock<MerkleTreeHashDigestProvider>()
    private val tree = mock<MerkleTree>()
    private val leaves = argumentCaptor<List<ByteArray>>()
    private val merkleTreeProvider = mock<MerkleTreeProvider> {
        on {
            createHashDigestProvider(
                eq(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME),
                eq(DigestAlgorithmName.SHA2_256),
                argThat {
                    this[HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION] is ByteArray &&
                        this[HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION] is ByteArray
                }
            )
        } doReturn digestProvider
        on { createTree(leaves.capture(), eq(digestProvider)) } doReturn tree
    }
    private val aliceMgmContext = mockLayeredPropertyMap<MGMContext>("context", "alice")
    private val aliceMgmContextBytes = byteArrayOf(1)
    private val aliceMemberContext = mockLayeredPropertyMap<MemberContext>("member", "alice")
    private val aliceMemberContextBytes = byteArrayOf(2, 3)
    private val bobMgmContext = mockLayeredPropertyMap<MGMContext>("context", "bob")
    private val bobMgmContextBytes = byteArrayOf(4, 5)
    private val bobMemberContext = mockLayeredPropertyMap<MemberContext>("member", "bob")
    private val bobMemberContextBytes = byteArrayOf(6, 7, 8)
    private val cordaAvroSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(eq(aliceMgmContext.toAvro())) } doReturn aliceMgmContextBytes
        on { serialize(eq(aliceMemberContext.toAvro())) } doReturn aliceMemberContextBytes
        on { serialize(eq(bobMgmContext.toAvro())) } doReturn bobMgmContextBytes
        on { serialize(eq(bobMemberContext.toAvro())) } doReturn bobMemberContextBytes
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn cordaAvroSerializer
    }
    private val signatureSpec = CryptoSignatureSpec("dummy", null, null)
    private val aliceName = MemberX500Name.parse("C=GB, CN=Alice, O=Alice Corp, L=LDN")
    private val alice = mock<SelfSignedMemberInfo> {
        on { name } doReturn aliceName
        on { mgmProvidedContext } doReturn aliceMgmContext
        on { memberProvidedContext } doReturn aliceMemberContext
        on { memberContextBytes } doReturn aliceMemberContextBytes
        on { mgmContextBytes } doReturn aliceMgmContextBytes
        on { memberSignature } doReturn CryptoSignatureWithKey(
            ByteBuffer.wrap("pk-$aliceName".toByteArray()),
            ByteBuffer.wrap("sig-$aliceName".toByteArray())
        )
        on { memberSignatureSpec } doReturn signatureSpec
    }
    private val bobName = MemberX500Name.parse("C=GB, CN=Bob, O=Bob Corp, L=LDN")
    private val bob = mock<SelfSignedMemberInfo> {
        on { name } doReturn bobName
        on { mgmProvidedContext } doReturn bobMgmContext
        on { memberProvidedContext } doReturn bobMemberContext
        on { memberContextBytes } doReturn bobMemberContextBytes
        on { mgmContextBytes } doReturn bobMgmContextBytes
        on { memberSignature } doReturn CryptoSignatureWithKey(
            ByteBuffer.wrap("pk-$bobName".toByteArray()),
            ByteBuffer.wrap("sig-$bobName".toByteArray())
        )
        on { memberSignatureSpec } doReturn signatureSpec
    }

    private val generator = MerkleTreeGenerator(
        merkleTreeProvider,
        cordaAvroSerializationFactory,
    )

    @Test
    fun `generateTree using members without signature sends the correct data`() {
        generator.generateTreeUsingMembers(listOf(bob, alice))

        assertThat(leaves.firstValue).containsExactly(
            aliceMemberContextBytes,
            aliceMgmContextBytes,
            bobMemberContextBytes,
            bobMgmContextBytes,
        )
    }

    @Test
    fun `generateTree using signed members sends the correct data`() {
        generator.generateTreeUsingSignedMembers(listOf(bob, alice))

        assertThat(leaves.firstValue).containsExactly(
            aliceMemberContextBytes,
            aliceMgmContextBytes,
            bobMemberContextBytes,
            bobMgmContextBytes,
        )
    }

    @Test
    fun `generateTree returns the correct data`() {
        val generatedTree = generator.generateTreeUsingSignedMembers(listOf(bob, alice))

        assertThat(generatedTree).isEqualTo(tree)
    }

    @Test
    fun `createTree returns the correct data`() {
        val generatedTree = generator.createTree(
            listOf(
                aliceMemberContextBytes,
                aliceMgmContextBytes,
                bobMemberContextBytes,
                bobMgmContextBytes,
            )
        )

        assertThat(generatedTree).isEqualTo(tree)
    }

    private inline fun <reified T : LayeredPropertyMap> mockLayeredPropertyMap(
        key: String,
        value: String
    ): T {
        val map = mapOf(key to value)
        return mock {
            on { entries } doReturn map.entries
        }
    }
}
