package net.corda.membership.datamodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HostedIdentityEntityTest {
    private val knownHoldingId = "0123456789AB"
    private val tlsCertAlias = "tls"
    private val sessionKeyId = "session"

    @Nested
    inner class EqualityAndHashTests {
        @Test
        fun `entities are equal if holding identity IDs match`() {
            val entity1 = HostedIdentityEntity(
                knownHoldingId,
                tlsCertAlias,
                sessionKeyId,
                true,
                1
            )
            val entity2 = HostedIdentityEntity(
                knownHoldingId,
                tlsCertAlias,
                sessionKeyId,
                true,
                1
            )

            assertEquals(entity1, entity2)
            assertEquals(entity1.hashCode(), entity2.hashCode())
        }

        @Test
        fun `entities are not equal if holding identity IDs do not match`() {
            val entity1 = HostedIdentityEntity(
                knownHoldingId,
                tlsCertAlias,
                sessionKeyId,
                true,
                1
            )
            val entity2 = HostedIdentityEntity(
                "different",
                tlsCertAlias,
                sessionKeyId,
                true,
                1
            )

            assertNotEquals(entity1, entity2)
            assertNotEquals(entity1.hashCode(), entity2.hashCode())
        }

        @Test
        fun `same instance is equal`() {
            val entity1 = HostedIdentityEntity(
                knownHoldingId,
                tlsCertAlias,
                sessionKeyId,
                true,
                1
            )

            assertEquals(entity1, entity1)
            assertEquals(entity1.hashCode(), entity1.hashCode())
        }

        @Test
        fun `instance is not equal to null`() {
            assertNotEquals(
                HostedIdentityEntity(
                    knownHoldingId,
                    tlsCertAlias,
                    sessionKeyId,
                    true,
                    1
                ),
                null
            )
        }

        @Test
        fun `instance is not equal to different class type`() {
            assertNotEquals(
                HostedIdentityEntity(
                    knownHoldingId,
                    tlsCertAlias,
                    sessionKeyId,
                    true,
                    1
                ),
                ""
            )
        }
    }
}
