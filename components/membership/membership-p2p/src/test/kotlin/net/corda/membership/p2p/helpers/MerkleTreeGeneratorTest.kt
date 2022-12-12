package net.corda.membership.p2p.helpers

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.toAvro
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class MerkleTreeGeneratorTest {
    private val digestProvider = mock<MerkleTreeHashDigestProvider>()
    private val tree = mock<MerkleTree>()
    private val leaves = argumentCaptor<List<ByteArray>>()
    private val merkleTreeProvider = mock<MerkleTreeProvider> {
        on {
            createHashDigestProvider(
                eq(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME),
                eq(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME),
                argThat {
                    this[HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION] is ByteArray &&
                        this[HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION] is ByteArray
                }
            )
        } doReturn digestProvider
        on { createTree(leaves.capture(), eq(digestProvider)) } doReturn tree
    }
    private val aliceContext = mockLayeredPropertyMap<MGMContext>("context", "alice")
    private val aliceContextSerialized = byteArrayOf(1)
    private val aliceMember = mockLayeredPropertyMap<MemberContext>("member", "alice")
    private val aliceMemberSerialized = byteArrayOf(2, 3)
    private val bobContext = mockLayeredPropertyMap<MGMContext>("context", "bob")
    private val bobContextSerialized = byteArrayOf(4, 5)
    private val bobMember = mockLayeredPropertyMap<MemberContext>("member", "bob")
    private val bobMemberSerialized = byteArrayOf(6, 7, 8)
    private val cordaAvroSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(eq(aliceContext.toAvro())) } doReturn aliceContextSerialized
        on { serialize(eq(aliceMember.toAvro())) } doReturn aliceMemberSerialized
        on { serialize(eq(bobContext.toAvro())) } doReturn bobContextSerialized
        on { serialize(eq(bobMember.toAvro())) } doReturn bobMemberSerialized
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn cordaAvroSerializer
    }
    private val alice = mock<MemberInfo> {
        on { name } doReturn MemberX500Name.parse("C=GB, CN=Alice, O=Alice Corp, L=LDN")
        on { mgmProvidedContext } doReturn aliceContext
        on { memberProvidedContext } doReturn aliceMember
    }
    private val bob = mock<MemberInfo> {
        on { name } doReturn MemberX500Name.parse("C=GB, CN=Bob, O=Bob Corp, L=LDN")
        on { mgmProvidedContext } doReturn bobContext
        on { memberProvidedContext } doReturn bobMember
    }

    private val generator = MerkleTreeGenerator(
        merkleTreeProvider,
        cordaAvroSerializationFactory,
    )

    @Test
    fun `generateTree sends the correct data`() {
        generator.generateTree(listOf(bob, alice))

        assertThat(leaves.firstValue).containsExactly(
            aliceMemberSerialized,
            aliceContextSerialized,
            bobMemberSerialized,
            bobContextSerialized,
        )
    }

    @Test
    fun `generateTree returns the correct data`() {
        val generatedTree = generator.generateTree(listOf(bob, alice))

        assertThat(generatedTree).isEqualTo(tree)
    }

    @Test
    fun `createTree returns the correct data`() {
        val generatedTree = generator.createTree(
            listOf(
                aliceMemberSerialized,
                aliceContextSerialized,
                bobMemberSerialized,
                bobContextSerialized,
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
