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
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound

class SessionMetadataTest {
    companion object {
        const val SESSION_EXPIRED_TIMESTAMP = 15000L
        const val SESSION_EXPIRY_TIMESTAMP = 10000L
        const val SESSION_UNEXPIRED_TIMESTAMP = 5000L
    }

    @Nested
    inner class InboundFromTests {
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

            assertThat(inboundSessionMetadata.commonData.source).isEqualTo(
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

            assertThat(inboundSessionMetadata.commonData.destination).isEqualTo(
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

            assertThat(inboundSessionMetadata.commonData.lastSendTimestamp).isEqualTo(
                Instant.ofEpochMilli(50L),
            )
        }

        @Test
        fun `it return the correct status`() {
            val inboundSessionMetadata = metadata.toInbound()

            assertThat(inboundSessionMetadata.status).isEqualTo(
                InboundSessionStatus.SentResponderHello,
            )
        }

        @Test
        fun `it return the correct expiry`() {
            val inboundSessionMetadata = metadata.toInbound()

            assertThat(inboundSessionMetadata.commonData.expiry).isEqualTo(
                Instant.ofEpochMilli(1000),
            )
        }
    }

    @Nested
    inner class OutboundFromTests {
        private val metadata = Metadata(
            mapOf(
                "sourceVnode" to "O=Alice, L=London, C=GB",
                "destinationVnode" to "O=Bob, L=London, C=GB",
                "groupId" to "group ID",
                "lastSendTimestamp" to 50L,
                "expiry" to 1000L,
                "status" to "SessionReady",
                "sessionId" to "Session ID",
                "serial" to 4L,
                "membershipStatus" to MembershipStatusFilter.ACTIVE_OR_SUSPENDED.toString(),
                "communicationWithMgm" to true,
                "initiationTimestampMillis" to 10L,
            ),
        )

        @Test
        fun `it return the correct source`() {
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.commonData.source).isEqualTo(
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
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.commonData.destination).isEqualTo(
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
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.commonData.lastSendTimestamp).isEqualTo(
                Instant.ofEpochMilli(50L),
            )
        }

        @Test
        fun `it return the correct expiry`() {
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.commonData.expiry).isEqualTo(
                Instant.ofEpochMilli(1000),
            )
        }

        @Test
        fun `it return the correct status`() {
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.status).isEqualTo(
                OutboundSessionStatus.SessionReady,
            )
        }

        @Test
        fun `it return the correct session id`() {
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.sessionId).isEqualTo("Session ID")
        }

        @Test
        fun `it return the correct serial`() {
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.serial).isEqualTo(4L)
        }

        @Test
        fun `it return the correct membershipStatus`() {
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.membershipStatus).isEqualTo(MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
        }

        @Test
        fun `it return the correct communicationWithMgm`() {
            val inboundSessionMetadata = metadata.toOutbound()

            assertThat(inboundSessionMetadata.communicationWithMgm).isTrue()
        }

    }

    @Nested
    inner class LastSendExpiredTests {
        private val metadata = InboundSessionMetadata(
            CommonMetadata(
                mock(),
                mock(),
                Instant.ofEpochMilli(10000L),
                mock(),
            ),
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
        private val inboundMetadata = InboundSessionMetadata(
            CommonMetadata(
                mock(),
                mock(),
                mock(),
                Instant.ofEpochMilli(SESSION_EXPIRY_TIMESTAMP),
            ),
            mock(),
        )
        private val outboundMetadata = OutboundSessionMetadata(
            CommonMetadata(
                mock(),
                mock(),
                mock(),
                Instant.ofEpochMilli(SESSION_EXPIRY_TIMESTAMP),
            ),
            "",
            mock(),
            0L,
            mock(),
            true,
            Instant.ofEpochMilli(SESSION_UNEXPIRED_TIMESTAMP),
        )

        @Test
        fun `it return true if the session expiration passed for inbound metadata`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(SESSION_EXPIRED_TIMESTAMP)
            }

            assertThat(inboundMetadata.sessionExpired(clock)).isTrue()
        }

        @Test
        fun `it return false if the session had not expired for inbound metadata`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(SESSION_UNEXPIRED_TIMESTAMP)
            }

            assertThat(inboundMetadata.sessionExpired(clock)).isFalse()
        }

        @Test
        fun `it return true if the session expiration passed for outbound metadata`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(SESSION_EXPIRED_TIMESTAMP)
            }

            assertThat(outboundMetadata.sessionExpired(clock)).isTrue()
        }

        @Test
        fun `it return false if the session had not expired for outbound metadata`() {
            val clock = mock<Clock> {
                on { instant() } doReturn Instant.ofEpochMilli(SESSION_UNEXPIRED_TIMESTAMP)
            }

            assertThat(outboundMetadata.sessionExpired(clock)).isFalse()
        }
    }

    @Nested
    inner class ToMetadataTests {
        private val inboundSessionMetadata = InboundSessionMetadata(
            commonData = CommonMetadata(
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
            Instant.ofEpochMilli(2000),
            ),
            InboundSessionStatus.SentResponderHandshake,
        )

        private val outboundSessionMetadata = OutboundSessionMetadata(
            commonData = CommonMetadata(
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
                Instant.ofEpochMilli(1001),
                Instant.ofEpochMilli(2002),
            ),
            sessionId = "Session ID",
            status = OutboundSessionStatus.SentInitiatorHello,
            serial = 42L,
            membershipStatus = MembershipStatusFilter.ACTIVE_OR_SUSPENDED,
            communicationWithMgm = true,
            Instant.ofEpochMilli(1001),
        )

        @Test
        fun `inbound metadata returns the correct data`() {
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

        @Test
        fun `outbound metadata returns the correct data`() {
            val metadata = outboundSessionMetadata.toMetadata()

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
                1001L,
            ).containsEntry(
                "expiry",
                2002L,
            ).containsEntry(
                "sessionId",
                "Session ID"
            ).containsEntry(
                "status",
                "SentInitiatorHello",
            ).containsEntry(
                "serial",
                42L
            ).containsEntry(
                "membershipStatus",
                "ACTIVE_OR_SUSPENDED"
            ).containsEntry(
                "communicationWithMgm",
                true
            )
        }
    }
}
