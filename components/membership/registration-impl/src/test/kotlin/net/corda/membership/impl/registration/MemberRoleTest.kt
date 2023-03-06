package net.corda.membership.impl.registration

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.membership.impl.registration.MemberRole.Companion.extractRolesFromContext
import net.corda.membership.impl.registration.MemberRole.Companion.toMemberInfo
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MemberRoleTest {
    @Test
    fun `no roles return empty collection`() {
        assertThat(extractRolesFromContext(emptyMap()))
            .isEmpty()
    }

    @Test
    fun `notary roles returns Notary`() {
        assertThat(
            extractRolesFromContext(
                mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
                )
            )
        )
            .hasSize(1)
            .allMatch {
                it == MemberRole.Notary(
                    MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"),
                    "net.corda.notary.MyNotaryService"
                )
            }
    }

    @Test
    fun `ignores duplicates`() {
        assertThat(
            extractRolesFromContext(
                mapOf(
                    "corda.roles.0" to "notary",
                    "corda.roles.1" to "notary",
                    "corda.roles.2" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
                )
            )
        )
            .hasSize(1)
    }

    @Test
    fun `accept context when notary plugin is missing`() {
        val roles = extractRolesFromContext(
            mapOf(
                "corda.roles.0" to "notary",
                "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
            )
        )

        assertThat(roles.toList())
            .hasSize(1)
            .allSatisfy {
                it is MemberRole.Notary
            }
            .allSatisfy {
                assertThat((it as? MemberRole.Notary)?.plugin).isNull()
            }
    }

    @Test
    fun `throws exception if notary service is missing`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
                )
            )
        }
    }

    @Test
    fun `throws exception if notary service is not X500 name`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "NOP",
                    "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
                )
            )
        }
    }

    @Test
    fun `throws exception if type is unknown`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    "corda.roles.0" to "another",
                )
            )
        }
    }

    @Test
    fun `throws exception if we have notary key without notary member`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
                )
            )
        }
    }

    @Test
    fun `toMemberInfo returns the correct information`() {
        val key1Hash = PublicKeyHash.calculate("test".toByteArray())
        val key1 = mock<KeyDetails>() {
            on { pem } doReturn "pem1"
            on { hash } doReturn key1Hash
            on { spec } doReturn SignatureSpec.RSA_SHA256
        }
        val key2Hash = PublicKeyHash.calculate("test2".toByteArray())
        val key2 = mock<KeyDetails>() {
            on { pem } doReturn "pem2"
            on { hash } doReturn key2Hash
            on { spec } doReturn SignatureSpec.ECDSA_SHA512
        }
        val roles = extractRolesFromContext(
            mapOf(
                "corda.roles.0" to "notary",
                "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
            )
        )

        val info = roles.toMemberInfo {
            listOf(key1, key2)
        }

        assertThat(info)
            .containsExactlyInAnyOrder(
                "corda.roles.0" to "notary",
                "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
                "corda.notary.keys.0.pem" to "pem1",
                "corda.notary.keys.0.hash" to key1Hash.toString(),
                "corda.notary.keys.0.signature.spec" to "SHA256withRSA",
                "corda.notary.keys.1.pem" to "pem2",
                "corda.notary.keys.1.hash" to key2Hash.toString(),
                "corda.notary.keys.1.signature.spec" to "SHA512withECDSA",
            )
    }
}
