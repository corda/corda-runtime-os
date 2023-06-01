package net.corda.external.messaging.services.impl

import net.corda.external.messaging.services.ExternalMessagingRecordFactory
import net.corda.libs.external.messaging.entities.Route
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component(service = [ExternalMessagingRecordFactory::class])
class ExternalMessagingRecordFactoryImpl(
    private val clock: Clock,
    private val createRandomIdFn: () -> String
) : ExternalMessagingRecordFactory {

    @Suppress("Unused")
    @Activate
    constructor() : this(UTCClock(), { UUID.randomUUID().toString() })

    private val isoDateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)

    override fun createSendRecord(
        holdingId: String,
        route: Route,
        messageId: String,
        message: String
    ): Record<String, String> {
        return Record(
            topic = route.externalReceiveTopicName,
            key = messageId,
            value = message,
            headers = listOf(
                MessageHeaders.HOLDING_ID to holdingId,
                MessageHeaders.CHANNEL_NAME to route.channelName,
                MessageHeaders.CORRELATION_ID to createRandomIdFn(),
                MessageHeaders.CREATION_TIME_UTC to isoDateTimeFormatter.format(clock.instant())
            )
        )
    }
}
