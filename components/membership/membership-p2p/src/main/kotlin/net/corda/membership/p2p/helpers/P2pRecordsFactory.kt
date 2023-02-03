package net.corda.membership.p2p.helpers

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.temporal.ChronoUnit
import java.util.UUID

class P2pRecordsFactory(
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val clock: Clock,
) {
    companion object {
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun SmartConfig.getTtlMinutes(ttlConfiguration: String?): Long? {
            return ttlConfiguration?.let { configurationName ->
                val path = "$TTLS.$configurationName"
                if (this.getIsNull(path)) {
                    null
                } else {
                    this.getLong(path)
                }
            }
        }
    }

    /**
     * Creates an authenticated message for P2P communication.
     *
     * @param source The source of the message.
     * @param destination The destination of the message.
     * @param content The content of the message.
     * @param minutesToWait Optional parameter. If not defined default value will be null. Meaning, P2P will re-try
     * to send the message infinitely. If defined, P2P will be trying to deliver the message for that many minutes,
     * after which this message will be dropped from the p2p layer.
     *
     * @return The ready-to-send authenticated message record.
     */
    fun <T : Any> createAuthenticatedMessageRecord(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        content: T,
        minutesToWait: Long? = null,
        id: String = UUID.randomUUID().toString(),
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
    ): Record<String, AppMessage> {
        val data = cordaAvroSerializationFactory.createAvroSerializer<T> {
            logger.warn("Serialization failed")
        }.serialize(content)
            ?: throw CordaRuntimeException("Could not serialize $content")
        val header = AuthenticatedMessageHeader.newBuilder()
            .setDestination(destination)
            .setSource(source)
            .setTtl(minutesToWait?.let { clock.instant().plus(it, ChronoUnit.MINUTES) })
            .setMessageId(id)
            .setTraceId(null)
            .setSubsystem(MEMBERSHIP_P2P_SUBSYSTEM)
            .setStatusFilter(filter)
            .build()
        val message = AuthenticatedMessage.newBuilder()
            .setHeader(header)
            .setPayload(ByteBuffer.wrap(data))
            .build()
        val appMessage = AppMessage(message)

        return Record(
            P2P_OUT_TOPIC,
            "Membership: $source -> $destination",
            appMessage,
        )
    }
}
