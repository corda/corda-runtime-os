package net.corda.p2p.linkmanager.sessions.metadata

import java.time.Duration
import java.time.Instant
import net.corda.libs.statemanager.api.Metadata
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

enum class InboundSessionStatus{
    SentResponderHello, SentResponderHandshake
}

private fun String.statusFromString(): InboundSessionStatus {
    return InboundSessionStatus.values().first { it.toString() == this }
}

/**
 * [InboundSessionMetadata] represents the metadata stored in the State Manager for a session.
 *
 * @param lastSendTimestamp The last time a session negotiation message was sent.
 * @param expiry When the Session Expires and should be rotated.
 * @param status Where we are in the Session negotiation process.
 */
data class InboundSessionMetadata(
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val encryptionKeyId: String,
    val encryptionKeyTenant: String,
    val status: InboundSessionStatus,
    val expiry: Instant
) {
    private companion object {
        const val SOURCE_VNODE = "sourceVnode"
        const val DEST_VNODE = "destinationVnode"
        const val GROUP_ID_KEY = "groupId"
        const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        const val ENCRYPTION_KEY_ID = "encryptionKeyId"
        const val ENCRYPTION_TENANT = "encryptionTenant"
        const val STATUS = "status"
        const val EXPIRY = "expiry"
        val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)
        val MESSAGE_EXPIRY_PERIOD: Duration = Duration.ofSeconds(2L)
    }

    constructor(metadata: Metadata): this(
        HoldingIdentity(MemberX500Name.parse(metadata[SOURCE_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
        HoldingIdentity(MemberX500Name.parse(metadata[DEST_VNODE].toString()), metadata[GROUP_ID_KEY].toString()),
        Instant.ofEpochMilli(metadata[LAST_SEND_TIMESTAMP] as Long),
        metadata[ENCRYPTION_KEY_ID].toString(),
        metadata[ENCRYPTION_TENANT].toString(),
        metadata[STATUS].toString().statusFromString(),
        Instant.ofEpochMilli(metadata[EXPIRY] as Long)
    )

    fun lastSendExpired(): Boolean {
        return Instant.now() > lastSendTimestamp + MESSAGE_EXPIRY_PERIOD
    }

    fun sessionExpired(): Boolean {
        return Instant.now() > lastSendTimestamp + SESSION_EXPIRY_PERIOD
    }


    fun toMetadata(): Metadata{
        return Metadata(
            mapOf(
                SOURCE_VNODE to this.source.x500Name.toString(),
                DEST_VNODE to this.destination.x500Name.toString(),
                GROUP_ID_KEY to this.source.groupId,
                LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
                ENCRYPTION_KEY_ID to this.encryptionKeyId,
                ENCRYPTION_TENANT to this.encryptionKeyTenant,
                STATUS to this.status,
                EXPIRY to this.expiry.toEpochMilli()
            )
        )
    }
}