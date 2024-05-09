package net.corda.membership.datamodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HostedIdentitySessionKeyInfoEntityTest {
    private val knownHoldingId = "0123456789AB"
    private val sessionCertAlias = "cert"
    private val sessionKeyId = "key"

    @Nested
    inner class EqualityAndHashTests {
        @Test
        fun `entities are equal if holding identity IDs and session key IDs match`() {
            val entity1 = HostedIdentitySessionKeyInfoEntity(
                knownHoldingId,
                sessionKeyId,
                sessionCertAlias
            )
            val entity2 = HostedIdentitySessionKeyInfoEntity(
                knownHoldingId,
                sessionKeyId,
                sessionCertAlias
            )

            assertEquals(entity1, entity2)
            assertEquals(entity1.hashCode(), entity2.hashCode())
        }

        @Test
        fun `entities are not equal if holding identity IDs do not match`() {
            val entity1 = HostedIdentitySessionKeyInfoEntity(
                knownHoldingId,
                sessionKeyId,
                sessionCertAlias
            )
            val entity2 = HostedIdentitySessionKeyInfoEntity(
                "different",
                sessionKeyId,
                sessionCertAlias
            )

            assertNotEquals(entity1, entity2)
            assertNotEquals(entity1.hashCode(), entity2.hashCode())
        }

        @Test
        fun `entities are not equal if session key IDs do not match`() {
            val entity1 = HostedIdentitySessionKeyInfoEntity(
                knownHoldingId,
                sessionKeyId,
                sessionCertAlias
            )
            val entity2 = HostedIdentitySessionKeyInfoEntity(
                knownHoldingId,
                "different",
                sessionCertAlias
            )

            assertNotEquals(entity1, entity2)
            assertNotEquals(entity1.hashCode(), entity2.hashCode())
        }

        @Test
        fun `same instance is equal`() {
            val entity1 = HostedIdentitySessionKeyInfoEntity(
                knownHoldingId,
                sessionKeyId,
                sessionCertAlias
            )

            assertEquals(entity1, entity1)
            assertEquals(entity1.hashCode(), entity1.hashCode())
        }

        @Test
        fun `instance is not equal to null`() {
            assertNotEquals(
                HostedIdentitySessionKeyInfoEntity(
                    knownHoldingId,
                    sessionKeyId,
                    sessionCertAlias
                ),
                null
            )
        }

        @Test
        fun `instance is not equal to different class type`() {
            assertNotEquals(
                HostedIdentitySessionKeyInfoEntity(
                    knownHoldingId,
                    sessionKeyId,
                    sessionCertAlias
                ),
                ""
            )
        }
    }
}
