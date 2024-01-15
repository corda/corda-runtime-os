package net.corda.p2p.linkmanager.sessions.metadata

import net.corda.libs.statemanager.api.Metadata
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Duration
import java.time.Instant

internal enum class InboundSessionStatus {
    SentResponderHello,
    SentResponderHandshake,
}

internal data class InboundSessionMetadata(
    val source: HoldingIdentity,
    val destination: HoldingIdentity,
    val lastSendTimestamp: Instant,
    val encryptionKeyId: String,
    val encryptionKeyTenant: String,
    val status: InboundSessionStatus,
    val expiry: Instant,
) {
    companion object {
        private const val SOURCE_VNODE = "sourceVnode"
        private const val DEST_VNODE = "destinationVnode"
        private const val GROUP_ID_KEY = "groupId"
        private const val LAST_SEND_TIMESTAMP = "lastSendTimestamp"
        private const val ENCRYPTION_KEY_ID = "encryptionKeyId"
        private const val ENCRYPTION_TENANT = "encryptionTenant"
        private const val STATUS = "status"
        private const val EXPIRY = "expiry"
        private val SESSION_EXPIRY_PERIOD: Duration = Duration.ofDays(7)

        private fun String.statusFromString(): InboundSessionStatus {
            return InboundSessionStatus.values().first { it.toString() == this }
        }

        fun Metadata.from(): InboundSessionMetadata {
            return InboundSessionMetadata(
                HoldingIdentity(MemberX500Name.parse(this[SOURCE_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                HoldingIdentity(MemberX500Name.parse(this[DEST_VNODE].toString()), this[GROUP_ID_KEY].toString()),
                Instant.ofEpochMilli(this[LAST_SEND_TIMESTAMP] as Long),
                this[ENCRYPTION_KEY_ID].toString(),
                this[ENCRYPTION_TENANT].toString(),
                this[STATUS].toString().statusFromString(),
                Instant.ofEpochMilli(this[EXPIRY] as Long),
            )
        }
    }

    fun lastSendExpired(clock: Clock): Boolean {
        return clock.instant() > lastSendTimestamp + SESSION_EXPIRY_PERIOD
    }

    fun toMetadata(): Metadata {
        return Metadata(
            mapOf(
                SOURCE_VNODE to this.source.x500Name.toString(),
                DEST_VNODE to this.destination.x500Name.toString(),
                GROUP_ID_KEY to this.source.groupId,
                LAST_SEND_TIMESTAMP to this.lastSendTimestamp.toEpochMilli(),
                ENCRYPTION_KEY_ID to this.encryptionKeyId,
                ENCRYPTION_TENANT to this.encryptionKeyTenant,
                STATUS to this.status.toString(),
                EXPIRY to this.expiry.toEpochMilli(),
            ),
        )
    }
}
