package net.corda.membership.staticmemberlist

import net.corda.crypto.SigningService
import net.corda.crypto.SigningService.Companion.EMPTY_CONTEXT
import net.corda.membership.identity.EndpointInfoImpl
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.identity.MemberInfoExtension.Companion.endpoints
import net.corda.membership.identity.MemberInfoExtension.Companion.groupId
import net.corda.membership.identity.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.identity.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.identity.MemberInfoExtension.Companion.status
import net.corda.membership.impl.GroupPolicyExtension.Companion.GROUP_ID
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.KEY_ALIAS
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.MGM_KEY_ALIAS
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.ROTATED_KEY_ALIAS
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.STATIC_NETWORK_TEMPLATE
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.NAME
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.STATIC_MEMBERS
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.STATIC_MGM
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
        private const val knownKeyAsString = "1234"
        private val signingService: SigningService = Mockito.mock(SigningService::class.java)
        private val staticMemberTemplate: List<Map<String, String>> =
            listOf(
                mapOf(
                    NAME to "C=GB, L=London, O=Alice",
                    KEY_ALIAS to "alice-alias",
                    String.format(ROTATED_KEY_ALIAS, 1) to "alice-historic-alias-1",
                    MEMBER_STATUS to MEMBER_STATUS_ACTIVE,
                    String.format(ENDPOINT_URL, 1) to "https://alice.corda5.r3.com:10000",
                    String.format(ENDPOINT_PROTOCOL, 1) to "1"
                ),
                mapOf(
                    NAME to "C=GB, L=London, O=Bob",
                    KEY_ALIAS to "bob-alias",
                    String.format(ROTATED_KEY_ALIAS, 1) to "bob-historic-alias-1",
                    String.format(ROTATED_KEY_ALIAS, 2) to "bob-historic-alias-2",
                    MEMBER_STATUS to MEMBER_STATUS_ACTIVE,
                    String.format(ENDPOINT_URL, 1) to "https://bob.corda5.r3.com:10000",
                    String.format(ENDPOINT_PROTOCOL, 1) to "1"
                ),
                mapOf(
                    NAME to "C=GB, L=London, O=Charlie",
                    KEY_ALIAS to "charlie-alias",
                    MEMBER_STATUS to MEMBER_STATUS_SUSPENDED,
                    String.format(ENDPOINT_URL, 1) to "https://charlie.corda5.r3.com:10000",
                    String.format(ENDPOINT_PROTOCOL, 1) to "1",
                    String.format(ENDPOINT_URL, 2) to "https://charlie-dr.corda5.r3.com:10001",
                    String.format(ENDPOINT_PROTOCOL, 2) to "1"
                )
            )
        private val groupPolicy = GroupPolicyImpl(
            mapOf(
                GROUP_ID to "8bede5ed-257e-4f6b-bfd7-26bb3b4f7715",
                STATIC_NETWORK_TEMPLATE to mapOf(
                    STATIC_MGM to mapOf(
                        MGM_KEY_ALIAS to "mgm-alias"
                    ),
                    STATIC_MEMBERS to staticMemberTemplate
                )
            )
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            whenever(
                keyEncodingService.decodePublicKey(any<ByteArray>())
            ).thenReturn(Mockito.mock(PublicKey::class.java))
            whenever(
                keyEncodingService.decodePublicKey(eq(knownKeyAsString.toByteArray()))
            ).thenReturn(knownKey)
            whenever(
                keyEncodingService.decodePublicKey(any<String>())
            ).thenReturn(Mockito.mock(PublicKey::class.java))
            whenever(
                keyEncodingService.decodePublicKey(eq(knownKeyAsString))
            ).thenReturn(knownKey)
            whenever(
                keyEncodingService.encodeAsString(any())
            ).thenReturn("1")
            whenever(
                keyEncodingService.encodeAsString(knownKey)
            ).thenReturn(knownKeyAsString)
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
    fun `static member list is parsed correctly into memberinfo list`() {
        listOf(
            MemberX500Name("Alice", "London", "GB"),
            MemberX500Name("Bob", "London", "GB"),
            MemberX500Name("Charlie", "London", "GB")
        ).forEach { name ->
            val member = membershipGroupReader.lookup(name)!!
            when (member.name.organisation) {
                "Alice" -> {
                    assertEquals(2, member.identityKeys.size)
                    assertEquals(MEMBER_STATUS_ACTIVE, member.status)
                    assertEquals(
                        listOf(EndpointInfoImpl("https://alice.corda5.r3.com:10000", 1)),
                        member.endpoints
                    )
                }
                "Bob" -> {
                    assertEquals(3, member.identityKeys.size)
                    assertEquals(MEMBER_STATUS_ACTIVE, member.status)
                    assertEquals(
                        listOf(EndpointInfoImpl("https://bob.corda5.r3.com:10000", 1)),
                        member.endpoints
                    )
                }
                "Charlie" -> {
                    assertEquals(1, member.identityKeys.size)
                    assertEquals(MEMBER_STATUS_SUSPENDED, member.status)
                    assertEquals(
                        listOf(
                            EndpointInfoImpl("https://charlie.corda5.r3.com:10000", 1),
                            EndpointInfoImpl("https://charlie-dr.corda5.r3.com:10001", 1)
                        ),
                        member.endpoints
                    )
                }
            }
            assertEquals("8bede5ed-257e-4f6b-bfd7-26bb3b4f7715", member.groupId)
            assertEquals("5.0.0", member.softwareVersion)
            assertEquals(10, member.platformVersion)
            assertEquals(1, member.serial)
            assertNotNull(member.modifiedTime)
        }
    }

    @Test
    fun `lookup using valid member name successfully returns memberinfo`() {
        val bob = MemberX500Name("Bob", "London", "GB")
        membershipGroupReader.lookup(bob).apply {
            assertEquals(bob, this?.name)
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
        membershipGroupReader.lookup(knownKeyAsString.toByteArray()).apply {
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
