package net.corda.p2p.linkmanager.sessions.metadata

import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Duration
import java.time.Instant

enum class SessionStatus{
    SentInitiatorHello, SentInitiatorHandshake, SentResponderHello, SentResponderHandshake, SessionReady
}

private fun String.statusFromString(): SessionStatus {
    return SessionStatus.values().first { it.toString() == this }
}

private fun String.membershipStatusFromString(): MembershipStatusFilter {
    return MembershipStatusFilter.values().first { it.toString() == this }
}

data class InboundSessionMetadata(
    val sessionId: String,
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val encryptionKeyId: String,
    val encryptionKeyTenant: String,
    val status: SessionStatus,
    val expiry: Instant
) {
    private companion object {
        const val SESSION_ID = "sessionId"
        const val SOURCE_VNODE = "sourceVnode"
        const val DEST_VNODE = "destinationVnode"
        const val GROUP_ID_KEY = "groupId"
        const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        const val ENCRYPTION_KEY_ID = "encryptionKeyId"
        const val ENCRYPTION_TENANT = "encryptionTenant"
        const val STATUS = "status"
        const val EXPIRY = "expiry"
        val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)
    }

    constructor(metadata: Metadata): this(
        metadata[SESSION_ID].toString(),
        HoldingIdentity(MemberX500Name.parse(metadata[SOURCE_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
        HoldingIdentity(MemberX500Name.parse(metadata[DEST_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
        Instant.ofEpochMilli(metadata[LAST_SEND_TIMESTAMP] as Long),
        metadata[ENCRYPTION_KEY_ID].toString(),
        metadata[ENCRYPTION_TENANT].toString(),
        metadata[STATUS].toString().statusFromString(),
        Instant.ofEpochMilli(metadata[EXPIRY] as Long)
    )

    fun lastSendExpired(): Boolean {
        return Instant.now() > lastSendTimestamp + SESSION_EXPIRY_PERIOD
    }

    fun toMetadata(): Metadata{
        return Metadata(
            mapOf(
                SESSION_ID to this.sessionId,
                SOURCE_VNODE to this.source.x500Name.toString(),
                DEST_VNODE to this.destination.x500Name.toString(),
                GROUP_ID_KEY to this.source.groupId,
                LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
                ENCRYPTION_KEY_ID to this.encryptionKeyId,
                ENCRYPTION_TENANT to this.encryptionKeyTenant,
                STATUS to this.status,
                EXPIRY to this.expiry.toEpochMilli(),
            )
        )
    }
}

data class OutboundSessionMetadata(
    val sessionId: String,
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val encryptionKeyId: String,
    val encryptionKeyTenant: String,
    val status: SessionStatus,
    val expiry: Instant,
    val serial: Long,
    val membershipStatus: MembershipStatusFilter,
    val communicationWithMgm: Boolean,
) {
    private companion object {
        const val SESSION_ID = "sessionId"
        const val SOURCE_VNODE = "sourceVnode"
        const val DEST_VNODE = "destinationVnode"
        const val GROUP_ID_KEY = "groupId"
        const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        const val ENCRYPTION_KEY_ID = "encryptionKeyId"
        const val ENCRYPTION_TENANT = "encryptionTenant"
        const val STATUS = "status"
        const val EXPIRY = "expiry"
        const val SERIAL = "serial"
        const val MEMBERSHIP_STATUS = "membershipStatus"
        const val COMMUNICATION_WITH_MGM = "communicationWithMgm"
        val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)
    }

    constructor(metadata: Metadata): this(
        metadata[SESSION_ID].toString(),
        HoldingIdentity(MemberX500Name.parse(metadata[SOURCE_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
        HoldingIdentity(MemberX500Name.parse(metadata[DEST_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
        Instant.ofEpochMilli(metadata[LAST_SEND_TIMESTAMP] as Long),
        metadata[ENCRYPTION_KEY_ID].toString(),
        metadata[ENCRYPTION_TENANT].toString(),
        metadata[STATUS].toString().statusFromString(),
        Instant.ofEpochMilli(metadata[EXPIRY] as Long),
        metadata[SERIAL] as Long,
        metadata[MEMBERSHIP_STATUS].toString().membershipStatusFromString(),
        metadata[COMMUNICATION_WITH_MGM].toString().toBoolean(),
    )

    fun lastSendExpired(): Boolean {
        return Instant.now() > lastSendTimestamp + SESSION_EXPIRY_PERIOD
    }

    fun toMetadata(): Metadata{
        return Metadata(
            mapOf(
                SESSION_ID to this.sessionId,
                SOURCE_VNODE to this.source.x500Name.toString(),
                DEST_VNODE to this.destination.x500Name.toString(),
                GROUP_ID_KEY to this.source.groupId,
                LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
                ENCRYPTION_KEY_ID to this.encryptionKeyId,
                ENCRYPTION_TENANT to this.encryptionKeyTenant,
                STATUS to this.status,
                EXPIRY to this.expiry.toEpochMilli(),
                SERIAL to this.serial,
                MEMBERSHIP_STATUS to this.membershipStatus,
                COMMUNICATION_WITH_MGM to this.communicationWithMgm,
            )
        )
    }
}