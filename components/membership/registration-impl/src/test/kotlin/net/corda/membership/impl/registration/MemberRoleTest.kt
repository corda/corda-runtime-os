package net.corda.membership.impl.registration

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.membership.impl.registration.MemberRole.Companion.extractRolesFromContext
import net.corda.membership.impl.registration.MemberRole.Companion.toMemberInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.v5.base.types.MemberX500Name
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
                    "${ROLES_PREFIX}.0" to "notary",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1",
                    String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 1) to "2",
                )
            )
        )
            .hasSize(1)
            .allMatch {
                it == MemberRole.Notary(
                    MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"),
                    "net.corda.notary.MyNotaryService",
                    setOf(1, 2),
                )
            }
    }

    @Test
    fun `ignores duplicates`() {
        assertThat(
            extractRolesFromContext(
                mapOf(
                    "${ROLES_PREFIX}.0" to "notary",
                    "${ROLES_PREFIX}.1" to "notary",
                    "${ROLES_PREFIX}.2" to "notary",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                )
            )
        )
            .hasSize(1)
    }

    @Test
    fun `accept context when notary protocol is missing`() {
        val roles = extractRolesFromContext(
            mapOf(
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
            )
        )

        assertThat(roles.toList())
            .hasSize(1)
            .allSatisfy {
                it is MemberRole.Notary
            }
            .allSatisfy {
                assertThat((it as? MemberRole.Notary)?.protocol).isNull()
            }
    }

    @Test
    fun `protocol versions is empty when no versions are specified`() {
        val roles = extractRolesFromContext(
            mapOf(
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
            )
        )

        assertThat(roles.toList())
            .hasSize(1)
            .allSatisfy {
                it is MemberRole.Notary
            }
            .allSatisfy {
                assertThat((it as? MemberRole.Notary)?.protocolVersions).isEmpty()
            }
    }

    @Test
    fun `throws exception if notary service is missing`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    "${ROLES_PREFIX}.0" to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                )
            )
        }
    }

    @Test
    fun `throws exception if notary service is not X500 name`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    "${ROLES_PREFIX}.0" to "notary",
                    NOTARY_SERVICE_NAME to "NOP",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                )
            )
        }
    }

    @Test
    fun `throws exception if type is unknown`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    "${ROLES_PREFIX}.0" to "another",
                )
            )
        }
    }

    @Test
    fun `throws exception if we have notary key without notary member`() {
        assertThrows<IllegalArgumentException> {
            extractRolesFromContext(
                mapOf(
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                )
            )
        }
    }

    @Test
    fun `toMemberInfo returns the correct information`() {
        val key1Hash = PublicKeyHash.calculate("test".toByteArray())
        val key1 = mock<KeyDetails> {
            on { pem } doReturn "pem1"
            on { hash } doReturn key1Hash
            on { spec } doReturn SignatureSpecs.RSA_SHA256
        }
        val key2Hash = PublicKeyHash.calculate("test2".toByteArray())
        val key2 = mock<KeyDetails> {
            on { pem } doReturn "pem2"
            on { hash } doReturn key2Hash
            on { spec } doReturn SignatureSpecs.ECDSA_SHA512
        }
        val roles = extractRolesFromContext(
            mapOf(
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 1) to "2",
            )
        )

        val info = roles.toMemberInfo {
            listOf(key1, key2)
        }

        assertThat(info)
            .containsExactlyInAnyOrder(
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_KEY_PEM, 0) to "pem1",
                String.format(NOTARY_KEY_HASH, 0) to key1Hash.toString(),
                String.format(NOTARY_KEY_SPEC, 0) to "SHA256withRSA",
                String.format(NOTARY_KEY_PEM, 1) to "pem2",
                String.format(NOTARY_KEY_HASH, 1) to key2Hash.toString(),
                String.format(NOTARY_KEY_SPEC, 1) to "SHA512withECDSA",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 1) to "2",
            )
    }
}
