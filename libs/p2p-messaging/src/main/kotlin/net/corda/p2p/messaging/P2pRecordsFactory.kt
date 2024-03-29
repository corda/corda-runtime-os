package net.corda.p2p.messaging

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class Subsystem(val systemName: String) {
    MEMBERSHIP("membership"),
    LINK_MANAGER("link-manager")
}

class P2pRecordsFactory(
    private val clock: Clock,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Creates an authenticated message for P2P communication.
     *
     * @param source The source of the message.
     * @param destination The destination of the message.
     * @param content The content of the message.
     * @param subsystem The subsystem of the message, which will help processing of the message
     * based on filtering later on.
     * @param recordKey Optional parameter. The unique key per topic for a Record. If not defined a random [UUID].
     * @param minutesToWait Optional parameter. If not defined default value will be null. Meaning, P2P will re-try
     * to send the message infinitely. If defined, P2P will be trying to deliver the message for that many minutes,
     * after which this message will be dropped from the p2p layer.
     * @param messageId Optional parameter. The ID of the message. If not defined a random [UUID].
     * @param filter Optional parameter. The membership status of the member we want to send the message to.
     * If not defined active will be used. Should be only relevant from MGM's POV as MGM's can see multiple
     * statuses of a given member.
     *
     * @return The ready-to-send authenticated message record.
     */
    @Suppress("LongParameterList")
    fun <T : Any> createAuthenticatedMessageRecord(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        content: T,
        subsystem: Subsystem,
        recordKey: String = UUID.randomUUID().toString(),
        minutesToWait: Long? = null,
        messageId: String = UUID.randomUUID().toString(),
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
    ): Record<String, AppMessage> {
        val data = wrapWithNullErrorHandling({
            CordaRuntimeException("Could not serialize $content", it)
        }) {
            cordaAvroSerializationFactory.createAvroSerializer<T> {
                logger.warn("Serialization failed")
            }.serialize(content)
        }
        val header = AuthenticatedMessageHeader.newBuilder()
            .setDestination(destination)
            .setSource(source)
            .setTtl(minutesToWait?.let { clock.instant().plus(it, ChronoUnit.MINUTES) })
            .setMessageId(messageId)
            .setTraceId(null)
            .setSubsystem(subsystem.systemName)
            .setStatusFilter(filter)
            .build()
        val message = AuthenticatedMessage.newBuilder()
            .setHeader(header)
            .setPayload(ByteBuffer.wrap(data))
            .build()
        val appMessage = AppMessage(message)

        return Record(
            Schemas.P2P.P2P_OUT_TOPIC,
            recordKey,
            appMessage,
        )
    }
}
