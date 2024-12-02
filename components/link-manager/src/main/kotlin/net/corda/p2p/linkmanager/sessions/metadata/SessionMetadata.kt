package net.corda.p2p.linkmanager.sessions.metadata

import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Duration
import java.time.Instant

/**
 * Possible session statuses for outbound sessions.
 * Reflects where we are in the Session negotiation process.
 */
internal enum class OutboundSessionStatus {
    SentInitiatorHello,
    SentInitiatorHandshake,
    SessionReady,
}

/**
 * Possible session statuses for inbound sessions.
 * Reflects where we are in the Session negotiation process.
 */
internal enum class InboundSessionStatus {
    SentResponderHello,
    SentResponderHandshake,
}

/**
 * [InboundSessionMetadata] represents the metadata stored in the State Manager for an inbound session.
 *
 * @param commonData The common metadata of the session.
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
                this.toCommonMetadata(),
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
 * @param commonData The common metadata of the session.
 * @param sessionId The ID of the session. Outbound sessions are keyed by counterparty information, so
 * session IDs are stored in the metadata.
 * @param status Where we are in the Session negotiation process.
 * @param serial Serial of the destination's member information.
 * @param membershipStatus The status of the destination's member information.
 * @param communicationWithMgm Boolean value which flags if the session is between MGM and member.
 * @param initiationTimestamp Timestamp when the session was initiated.
 */
internal data class OutboundSessionMetadata(
    val commonData: CommonMetadata,
    val sessionId: String,
    val status: OutboundSessionStatus,
    val serial: Long,
    val membershipStatus: MembershipStatusFilter,
    val communicationWithMgm: Boolean,
    val initiationTimestamp: Instant,
) {
    companion object {
        private const val STATUS = "status"
        private const val SERIAL = "serial"
        private const val MEMBERSHIP_STATUS = "membershipStatus"
        private const val COMMUNICATION_WITH_MGM = "communicationWithMgm"
        private const val SESSION_ID = "sessionId"
        private const val INITIATION_TIMESTAMP_MILLIS = "initiationTimestampMillis"

        fun Metadata.toOutbound(): OutboundSessionMetadata {
            return OutboundSessionMetadata(
                this.toCommonMetadata(),
                this[SESSION_ID].toString(),
                this[STATUS].toString().statusFromString(),
                (this[SERIAL] as Number).toLong(),
                this[MEMBERSHIP_STATUS].toString().membershipStatusFromString(),
                this[COMMUNICATION_WITH_MGM].toString().toBoolean(),
                Instant.ofEpochMilli((this[INITIATION_TIMESTAMP_MILLIS] as Number).toLong()),
            )
        }

        private fun String.statusFromString(): OutboundSessionStatus {
            return OutboundSessionStatus.values().first { it.toString() == this }
        }

        private fun String.membershipStatusFromString(): MembershipStatusFilter {
            return MembershipStatusFilter.values().first { it.toString() == this }
        }

        fun Metadata.isOutbound(): Boolean = this[SESSION_ID] != null
    }

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
                    SESSION_ID to this.sessionId,
                    INITIATION_TIMESTAMP_MILLIS to this.initiationTimestamp.toEpochMilli(),
                ),
        )
    }
}

/**
 * [CommonMetadata] stores the common metadata in [OutboundSessionMetadata] and [InboundSessionMetadata].
 *
 * @param source The identity of the initiator.
 * @param destination The identity of the recipient.
 * @param lastSendTimestamp The last time a session negotiation message was sent.
 * @param expiry When the Session Expires and should be rotated.
 */
internal data class CommonMetadata(
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
        private val MESSAGE_EXPIRY_PERIOD: Duration = Duration.ofSeconds(2L)

        fun Metadata.toCommonMetadata(): CommonMetadata {
            return CommonMetadata(
                HoldingIdentity(MemberX500Name.parse(this[SOURCE_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                HoldingIdentity(MemberX500Name.parse(this[DEST_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                Instant.ofEpochMilli((this[LAST_SEND_TIMESTAMP] as Number).toLong()),
                Instant.ofEpochMilli((this[EXPIRY] as Number).toLong()),
            )
        }
    }

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
        return clock.instant() > expiry
    }
}

/**
 * Reads the counterparties from the state.
 */
internal fun State.toCounterparties(): SessionManager.Counterparties {
    val common = this.metadata.toCommonMetadata()
    return SessionManager.Counterparties(
        ourId = common.source,
        counterpartyId = common.destination,
    )
}
