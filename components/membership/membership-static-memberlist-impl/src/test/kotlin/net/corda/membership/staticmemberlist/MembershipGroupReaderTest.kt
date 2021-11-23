package net.corda.membership.staticmemberlist

import net.corda.crypto.SigningService
import net.corda.crypto.SigningService.Companion.EMPTY_CONTEXT
import net.corda.membership.staticmemberlist.GroupPolicyExtension.Companion.GROUP_ID
import net.corda.membership.staticmemberlist.GroupPolicyExtension.Companion.STATIC_MEMBER_TEMPLATE
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MembershipGroupReaderTest {
    companion object {
        private lateinit var membershipGroupReader: MembershipGroupReaderImpl
        private val keyEncodingService = Mockito.mock(KeyEncodingService::class.java)
        private val knownKey = Mockito.mock(PublicKey::class.java)
        val signingService: SigningService = Mockito.mock(SigningService::class.java)
        private val staticMemberTemplate: List<Map<String, String>> =
            listOf(
                mapOf(
                    "x500Name" to "C=GB, L=London, O=Alice",
                    "keyAlias" to "alice-alias",
                    "rotatedKeyAlias-1" to "alice-historic-alias-1",
                    "memberStatus" to "ACTIVE",
                    "endpointUrl-1" to "https://alice.corda5.r3.com:10000",
                    "endpointProtocol-1" to "1"
                ),
                mapOf(
                    "x500Name" to "C=GB, L=London, O=Bob",
                    "keyAlias" to "bob-alias",
                    "rotatedKeyAlias-1" to "bob-historic-alias-1",
                    "rotatedKeyAlias-2" to "bob-historic-alias-2",
                    "memberStatus" to "ACTIVE",
                    "endpointUrl-1" to "https://bob.corda5.r3.com:10000",
                    "endpointProtocol-1" to "1"
                ),
                mapOf(
                    "x500Name" to "C=GB, L=London, O=Charlie",
                    "keyAlias" to "charlie-alias",
                    "memberStatus" to "SUSPENDED",
                    "endpointUrl-1" to "https://charlie.corda5.r3.com:10000",
                    "endpointProtocol-1" to "1",
                    "endpointUrl-2" to "https://charlie-dr.corda5.r3.com:10001",
                    "endpointProtocol-2" to "1"
                )
            )
        val groupPolicy = GroupPolicyImpl(
            mapOf(
                GROUP_ID to "8bede5ed-257e-4f6b-bfd7-26bb3b4f7715",
                STATIC_MEMBER_TEMPLATE to staticMemberTemplate
            )
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            whenever(
                keyEncodingService.decodePublicKey(any<ByteArray>())
            ).thenReturn(Mockito.mock(PublicKey::class.java))
            whenever(
                keyEncodingService.decodePublicKey(eq("1234".toByteArray()))
            ).thenReturn(knownKey)
            whenever(
                keyEncodingService.decodePublicKey(any<String>())
            ).thenReturn(Mockito.mock(PublicKey::class.java))
            whenever(
                keyEncodingService.decodePublicKey(eq("1234"))
            ).thenReturn(knownKey)
            whenever(
                keyEncodingService.encodeAsString(any())
            ).thenReturn("1")
            whenever(
                keyEncodingService.encodeAsString(knownKey)
            ).thenReturn("1234")
            whenever(
                signingService.generateKeyPair(any(), eq(EMPTY_CONTEXT))
            ).thenReturn(Mockito.mock(PublicKey::class.java))
            whenever(
                signingService.generateKeyPair(eq("alice-alias"), eq(EMPTY_CONTEXT))
            ).thenReturn(knownKey)

            membershipGroupReader = MembershipGroupReaderImpl(
                MemberX500Name("Alice", "London", "GB"),
                groupPolicy,
                keyEncodingService,
                signingService
            )

        }
    }

    @Test
    fun `lookup using valid member name successfully returns memberinfo`() {
        membershipGroupReader.lookup(MemberX500Name("Bob", "London", "GB")).apply {
            assertEquals((MemberX500Name("Bob", "London", "GB")), this?.name)
        }
    }

    @Test
    fun `lookup using non-existing member name returns null`() {
        membershipGroupReader.lookup(MemberX500Name("Invalid", "London", "GB")).apply {
            assertNull(this)
        }
    }

    @Test
    fun `lookup using valid public key hash successfully returns memberinfo`() {
        membershipGroupReader.lookup("1234".toByteArray()).apply {
            assertEquals(MemberX500Name("Alice", "London", "GB"), this?.name)
        }
    }

    @Test
    fun `lookup using unknown public key hash returns null`() {
        membershipGroupReader.lookup("6789".toByteArray()).apply {
            assertNull(this)
        }
    }
}
