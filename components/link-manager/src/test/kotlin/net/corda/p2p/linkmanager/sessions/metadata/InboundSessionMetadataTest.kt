package net.corda.p2p.linkmanager.sessions.metadata

import net.corda.libs.statemanager.api.Metadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.toInbound
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Duration
import java.time.Instant

class InboundSessionMetadataTest {
    @Nested
    inner class FromTests {
        private val metadata = Metadata(
            mapOf(
                "sourceVnode" to "O=Alice, L=London, C=GB",
                "destinationVnode" to "O=Bob, L=London, C=GB",
                "groupId" to "group ID",
                "lastSendTimestamp" to 50L,
                "status" to "SentResponderHello",
                "expiry" to 1000L,
            ),
        )

        @Test
        fun `it return the correct source`() {
            val inboundSessionMetadata = metadata.toInbound()

            assertThat(inboundSessionMetadata.source).isEqualTo(
                HoldingIdentity(
                    MemberX500Name.parse(
                        "O=Alice, L=London, C=GB",
                    ),
                    "group ID",
                ),
            )
        }

        @Test
        fun `it return the correct destination`() {
            val inboundSessionMetadata = metadata.toInbound()

            assertThat(inboundSessionMetadata.destination).isEqualTo(
                HoldingIdentity(
                    MemberX500Name.parse(
                        "O=Bob, L=London, C=GB",
                    ),
                    "group ID",
                ),
            )
        }

        @Test
        fun `it return the correct lastSendTimestamp`() {
            val inboundSessionMetadata = metadata.toInbound()

            assertThat(inboundSessionMetadata.lastSendTimestamp).isEqualTo(
                Instant.ofEpochMilli(50L),
            )
        }

        @Test
        fun `it return the correct status`() {
            val inboundSessionMetadata = metadata.toInbound()

            assertThat(inboundSessionMetadata.status).isEqualTo(
                SessionStatus.SentResponderHello,
            )
        }

        @Test
        fun `it return the correct expiry`() {
            val inboundSessionMetadata = metadata.toInbound()

            assertThat(inboundSessionMetadata.expiry).isEqualTo(
                Instant.ofEpochMilli(1000),
            )
        }
    }

    @Nested
    inner class LastSendExpiredTests {
        private val metadata = InboundSessionMetadata(
            mock(),
            mock(),
            Instant.ofEpochMilli(10000L),
            mock(),
            mock(),
        )

        @Test
        fun `it return true if the last sent was expired`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(10000L + Duration.ofSeconds(8).toMillis())
            }

            assertThat(metadata.lastSendExpired(clock)).isTrue()
        }

        @Test
        fun `it return false if the last sent was not expired`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(10000L + Duration.ofSeconds(1).toMillis())
            }

            assertThat(metadata.lastSendExpired(clock)).isFalse()
        }
    }

    @Nested
    inner class SessionExpiredTest {
        private val metadata = InboundSessionMetadata(
            mock(),
            mock(),
            mock(),
            mock(),
            Instant.ofEpochMilli(10000L),
        )

        @Test
        fun `it return true if the session expiration passed`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(10000L + Duration.ofDays(8).toMillis())
            }

            assertThat(metadata.sessionExpired(clock)).isTrue()
        }

        @Test
        fun `it return false if the session had not expired`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(10000L + Duration.ofDays(6).toMillis())
            }

            assertThat(metadata.sessionExpired(clock)).isFalse()
        }
    }

    @Nested
    inner class ToMetadataTests {
        private val inboundSessionMetadata = InboundSessionMetadata(
            HoldingIdentity(
                MemberX500Name.parse(
                    "O=Alice, L=London, C=GB",
                ),
                "group ID",
            ),
            HoldingIdentity(
                MemberX500Name.parse(
                    "O=Bob, L=London, C=GB",
                ),
                "group ID",
            ),
            Instant.ofEpochMilli(1000),
            SessionStatus.SentResponderHandshake,
            Instant.ofEpochMilli(2000),
        )

        @Test
        fun `it returns the correct data`() {
            val metadata = inboundSessionMetadata.toMetadata()

            assertThat(metadata).containsEntry(
                "sourceVnode",
                "O=Alice, L=London, C=GB",
            ).containsEntry(
                "destinationVnode",
                "O=Bob, L=London, C=GB",
            ).containsEntry(
                "groupId",
                "group ID",
            ).containsEntry(
                "lastSendTimestamp",
                1000L,
            ).containsEntry(
                "status",
                "SentResponderHandshake",
            ).containsEntry(
                "expiry",
                2000L,
            )
        }
    }
}
