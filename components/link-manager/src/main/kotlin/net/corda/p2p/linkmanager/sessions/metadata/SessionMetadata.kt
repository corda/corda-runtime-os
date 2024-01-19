package net.corda.p2p.linkmanager.sessions.metadata

import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Duration
import java.time.Instant

enum class OutboundSessionStatus{
    SentInitiatorHello, SentInitiatorHandshake, SessionReady
}

private fun String.statusFromString(): OutboundSessionStatus {
    return OutboundSessionStatus.values().first { it.toString() == this }
}

private fun String.membershipStatusFromString(): MembershipStatusFilter {
    return MembershipStatusFilter.values().first { it.toString() == this }
}

internal enum class InboundSessionStatus {
    SentResponderHello,
    SentResponderHandshake,
}

/**
 * [InboundSessionMetadata] represents the metadata stored in the State Manager for an inbound session.
 *
 * @param status Where we are in the Session negotiation process.
 */
internal data class InboundSessionMetadata(
    val commonData: CommonMetadata,
    val status: InboundSessionStatus,
) {
    companion object {
        private const val STATUS = "status"

        private fun String.statusFromString(): InboundSessionStatus {
            return InboundSessionStatus.values().first { it.toString() == this }
        }

        fun Metadata.toInbound(): InboundSessionMetadata {
            return InboundSessionMetadata(
                CommonMetadata(this),
                this[STATUS].toString().statusFromString(),
            )
        }
    }

    fun toMetadata(): Metadata {
        return Metadata(commonData.metadataMap + mapOf(STATUS to this.status.toString()))
    }

    fun lastSendExpired(clock: Clock): Boolean {
        return commonData.lastSendExpired(clock)
    }

    fun sessionExpired(clock: Clock): Boolean {
        return commonData.sessionExpired(clock)
    }
}

/**
 * [OutboundSessionMetadata] represents the metadata stored in the State Manager for an outbound session.
 *
 * @param status Where we are in the Session negotiation process.
 */
data class OutboundSessionMetadata(
    val commonData: CommonMetadata,
    val sessionId: String,
    val status: OutboundSessionStatus,
    val serial: Long,
    val membershipStatus: MembershipStatusFilter,
    val communicationWithMgm: Boolean,
) {
    companion object {
        private const val STATUS = "status"
        private const val SERIAL = "serial"
        private const val MEMBERSHIP_STATUS = "membershipStatus"
        private const val COMMUNICATION_WITH_MGM = "communicationWithMgm"
        private const val SESSION_ID = "sessionId"

        fun Metadata.toOutbound(): OutboundSessionMetadata {
            return OutboundSessionMetadata(this)
        }
    }

    constructor(metadata: Metadata): this(
        CommonMetadata(metadata),
        metadata[SESSION_ID].toString(),
        metadata[STATUS].toString().statusFromString(),
        metadata[SERIAL] as Long,
        metadata[MEMBERSHIP_STATUS].toString().membershipStatusFromString(),
        metadata[COMMUNICATION_WITH_MGM].toString().toBoolean(),
    )

    fun lastSendExpired(clock: Clock): Boolean {
        return commonData.lastSendExpired(clock)
    }

    fun sessionExpired(clock: Clock): Boolean {
        return commonData.sessionExpired(clock)
    }

    fun toMetadata(): Metadata {
        return Metadata(
            commonData.metadataMap +
            mapOf(
                STATUS to this.status.toString(),
                SERIAL to this.serial,
                MEMBERSHIP_STATUS to this.membershipStatus.toString(),
                COMMUNICATION_WITH_MGM to this.communicationWithMgm,
                SESSION_ID to this.sessionId
            )
        )
    }
}

/**
 * [CommonMetadata] stores the common metadata in [OutboundSessionMetadata] and [InboundSessionMetadata].
 *
 * @param lastSendTimestamp The last time a session negotiation message was sent.
 * @param expiry When the Session Expires and should be rotated.
 */
data class CommonMetadata(
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val expiry: Instant,
) {
    companion object {
        private const val SOURCE_VNODE = "sourceVnode"
        private const val DEST_VNODE = "destinationVnode"
        private const val GROUP_ID_KEY = "groupId"
        private const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        private const val EXPIRY = "expiry"
        private val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)
        private val MESSAGE_EXPIRY_PERIOD: Duration = Duration.ofSeconds(2L)
    }

    constructor(metadata: Metadata):
        this(
            HoldingIdentity(MemberX500Name.parse(metadata[SOURCE_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
            HoldingIdentity(MemberX500Name.parse(metadata[DEST_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
            Instant.ofEpochMilli(metadata[LAST_SEND_TIMESTAMP] as Long),
            Instant.ofEpochMilli(metadata[EXPIRY] as Long),
        )

    internal val metadataMap by lazy {
        mapOf(
            SOURCE_VNODE to this.source.x500Name.toString(),
            DEST_VNODE to this.destination.x500Name.toString(),
            GROUP_ID_KEY to this.source.groupId,
            LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
            EXPIRY to this.expiry.toEpochMilli(),
        )
    }

    fun lastSendExpired(clock: Clock): Boolean {
        return clock.instant() > lastSendTimestamp + MESSAGE_EXPIRY_PERIOD
    }

    fun sessionExpired(clock: Clock): Boolean {
        return clock.instant() > expiry + SESSION_EXPIRY_PERIOD
    }

    fun toMetadata(): Metadata {
        return Metadata(metadataMap)
    }
}