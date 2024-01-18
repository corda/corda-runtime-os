package net.corda.p2p.linkmanager.sessions.metadata

import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Duration
import java.time.Instant

internal enum class SessionStatus {
    SentInitiatorHello,
    SentInitiatorHandshake,
    SentResponderHello,
    SentResponderHandshake,
    SessionReady
}

/**
 * [InboundSessionMetadata] represents the metadata stored in the State Manager for a session.
 *
 * @param lastSendTimestamp The last time a session negotiation message was sent.
 * @param expiry When the Session Expires and should be rotated.
 * @param status Where we are in the Session negotiation process.
 */
internal data class InboundSessionMetadata(
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val status: SessionStatus,
    val expiry: Instant,
) {
    companion object {
        private const val SOURCE_VNODE = "sourceVnode"
        private const val DEST_VNODE = "destinationVnode"
        private const val GROUP_ID_KEY = "groupId"
        private const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        private const val STATUS = "status"
        private const val EXPIRY = "expiry"
        private val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)
        private val MESSAGE_EXPIRY_PERIOD: Duration = Duration.ofSeconds(2L)

        private fun String.statusFromString(): SessionStatus {
            return SessionStatus.values().first { it.toString() == this }
        }

        fun Metadata.toInbound(): InboundSessionMetadata {
            return InboundSessionMetadata(
                HoldingIdentity(MemberX500Name.parse(this[SOURCE_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                HoldingIdentity(MemberX500Name.parse(this[DEST_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                Instant.ofEpochMilli(this[LAST_SEND_TIMESTAMP] as Long),
                this[STATUS].toString().statusFromString(),
                Instant.ofEpochMilli(this[EXPIRY] as Long),
            )
        }
    }

    fun lastSendExpired(clock: Clock): Boolean {
        return clock.instant() > lastSendTimestamp + MESSAGE_EXPIRY_PERIOD
    }

    fun sessionExpired(clock: Clock): Boolean {
        return clock.instant() > expiry + SESSION_EXPIRY_PERIOD
    }

    fun toMetadata(): Metadata {
        return Metadata(
            mapOf(
                SOURCE_VNODE to this.source.x500Name.toString(),
                DEST_VNODE to this.destination.x500Name.toString(),
                GROUP_ID_KEY to this.source.groupId,
                LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
                STATUS to this.status.toString(),
                EXPIRY to this.expiry.toEpochMilli(),
            ),
        )
    }
}

internal data class OutboundSessionMetadata(
    val sessionId: String,
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val status: SessionStatus,
    val expiry: Instant,
    val serial: Long,
    val membershipStatus: MembershipStatusFilter,
    val communicationWithMgm: Boolean,
) {
    companion object {
        private const val SESSION_ID = "sessionId"
        private const val SOURCE_VNODE = "sourceVnode"
        private const val DEST_VNODE = "destinationVnode"
        private const val GROUP_ID_KEY = "groupId"
        private const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        private const val STATUS = "status"
        private const val EXPIRY = "expiry"
        const val SERIAL = "serial"
        const val MEMBERSHIP_STATUS = "membershipStatus"
        const val COMMUNICATION_WITH_MGM = "communicationWithMgm"
        private val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)
        private val MESSAGE_EXPIRY_PERIOD: Duration = Duration.ofSeconds(2L)

        private fun String.statusFromString(): SessionStatus {
            return SessionStatus.values().first { it.toString() == this }
        }

        private fun String.membershipStatusFromString(): MembershipStatusFilter {
            return MembershipStatusFilter.values().first { it.toString() == this }
        }

        fun Metadata.toOutbound(): OutboundSessionMetadata {
            return OutboundSessionMetadata(
                this[SESSION_ID].toString(),
                HoldingIdentity(MemberX500Name.parse(this[SOURCE_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                HoldingIdentity(MemberX500Name.parse(this[DEST_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                Instant.ofEpochMilli(this[LAST_SEND_TIMESTAMP] as Long),
                this[STATUS].toString().statusFromString(),
                Instant.ofEpochMilli(this[EXPIRY] as Long),
                this[SERIAL] as Long,
                this[MEMBERSHIP_STATUS].toString().membershipStatusFromString(),
                this[COMMUNICATION_WITH_MGM].toString().toBoolean(),
            )
        }
    }

    fun lastSendExpired(clock: Clock): Boolean {
        return clock.instant() > lastSendTimestamp + MESSAGE_EXPIRY_PERIOD
    }

    fun sessionExpired(clock: Clock): Boolean {
        return clock.instant() > expiry + SESSION_EXPIRY_PERIOD
    }

    fun toMetadata(): Metadata {
        return Metadata(
            mapOf(
                SESSION_ID to this.sessionId,
                SOURCE_VNODE to this.source.x500Name.toString(),
                DEST_VNODE to this.destination.x500Name.toString(),
                GROUP_ID_KEY to this.source.groupId,
                LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
                STATUS to this.status.toString(),
                EXPIRY to this.expiry.toEpochMilli(),
                SERIAL to this.serial,
                MEMBERSHIP_STATUS to this.membershipStatus,
                COMMUNICATION_WITH_MGM to this.communicationWithMgm,
            ),
        )
    }
}
