package net.corda.flow.pipeline.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.handlers.addOrReplaceSession
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowGlobalPostProcessor::class])
class FlowGlobalPostProcessorImpl @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowGlobalPostProcessor {

    private companion object {
        private val testConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
            .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
        private val configFactory = SmartConfigFactory.create(testConfig)
        private val testSmartConfig = configFactory.create(testConfig)
    }

    override fun postProcess(context: FlowEventContext<Any>): FlowEventContext<Any> {

        val now = Instant.now()

        context.checkpoint?.let { checkpoint ->

            val records = checkpoint.sessions
                .map { sessionState -> sessionManager.getMessagesToSend(sessionState, now, testSmartConfig, checkpoint.flowKey.identity) }
                .onEach { (updatedSessionState, _) -> checkpoint.addOrReplaceSession(updatedSessionState) }
                .flatMap { (_, messages) -> messages }
                .map { message -> message.toRecord() }

            return context.copy(outputRecords = context.outputRecords + records)
        }

        return context
    }

    private fun SessionEvent.toRecord() = Record(
        topic = Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC,
        key = sessionId,
        value = FlowMapperEvent(this)
    )
}