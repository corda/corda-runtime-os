package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_SESSION_EXPIRY_KEY
import net.corda.libs.statemanager.api.Metadata
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW
import net.corda.schema.configuration.FlowConfig.SESSION_FLOW_CLEANUP_TIME
import net.corda.schema.configuration.FlowConfig.SESSION_TIMEOUT_WINDOW
import net.corda.session.manager.SessionManager
import net.corda.utilities.debug
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@Component(service = [FlowGlobalPostProcessor::class])
class FlowGlobalPostProcessorImpl @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : FlowGlobalPostProcessor {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun postProcess(context: FlowEventContext<Any>): FlowEventContext<Any> {
        val now = Instant.now()

        postProcessPendingPlatformError(context)

        val outputRecords = getSessionEvents(context, now) +
                getFlowMapperSessionCleanupEvents(context, now) +
                getExternalEvent(context, now) +
                postProcessRetries(context)

        context.flowMetrics.flowEventCompleted(context.inputEvent.payload::class.java.name)
        val metadata = getStateMetadata(context)

        return context.copy(
            outputRecords = context.outputRecords + outputRecords,
            metadata = metadata
        )
    }

    private fun getSessionEvents(context: FlowEventContext<Any>, now: Instant): List<Record<*, FlowMapperEvent>> {
        val checkpoint = context.checkpoint
        val doesCheckpointExist = checkpoint.doesExist

        return checkpoint.sessions
            .filter {
                verifyCounterparty(context, it)
            }
            .map { sessionState ->
                sessionManager.getMessagesToSend(
                    sessionState,
                    now,
                    context.flowConfig,
                    checkpoint.flowKey.identity
                )
            }
            .onEach { (updatedSessionState, _) ->
                if (doesCheckpointExist) {
                    checkpoint.putSessionState(updatedSessionState)
                }
            }
            .flatMap { (_, events) -> events }
            .map { event ->
                context.flowMetrics.flowSessionMessageSent(event.payload::class.java.name)
                flowRecordFactory.createFlowMapperEventRecord(event.sessionId, event)
            }
    }

    private fun verifyCounterparty(
        context: FlowEventContext<Any>,
        sessionState: SessionState
    ): Boolean {
        if (sessionState.status == SessionStateType.CLOSED || sessionState.status == SessionStateType.ERROR) {
            //we dont need to verify that a counterparty exists if the session is already terminated.
            return true
        }
        val checkpoint = context.checkpoint
        val doesCheckpointExist = checkpoint.doesExist
        val counterparty: MemberX500Name = MemberX500Name.parse(sessionState.counterpartyIdentity.x500Name!!)
        val groupReader = membershipGroupReaderProvider.getGroupReader(context.checkpoint.holdingIdentity)
        val counterpartyExists: Boolean = null != groupReader.lookup(counterparty)

        /**
         * If the counterparty doesn't exist in our network, throw a [FlowPlatformException]
         */
        if (!counterpartyExists) {
            val msg = "[${context.checkpoint.holdingIdentity.x500Name}] has failed to create a flow with counterparty: " +
                    "[${counterparty}] as the recipient doesn't exist in the network."
            sessionManager.errorSession(sessionState)
            if (doesCheckpointExist) {
                log.debug { "$msg. Throwing FlowFatalException" }
                checkpoint.putSessionState(sessionState)
                throw FlowFatalException(msg)
            } else {
                log.debug { "$msg. Checkpoint is already marked for deletion." }
            }

            return false
        }

        return true
    }

    private fun getFlowMapperSessionCleanupEvents(
        context: FlowEventContext<Any>,
        now: Instant,
    ): List<Record<*, FlowMapperEvent>> {
        val flowCleanupTime = context.flowConfig.getLong(SESSION_FLOW_CLEANUP_TIME)
        val expiryTime = now.plusMillis(flowCleanupTime).toEpochMilli()
        return context.checkpoint.sessions
            .filterNot { sessionState -> sessionState.hasScheduledCleanup }
            .filter { sessionState -> sessionState.status == SessionStateType.CLOSED || sessionState.status == SessionStateType.ERROR }
            .onEach { sessionState -> sessionState.hasScheduledCleanup = true }
            .map { sessionState ->
                flowRecordFactory.createFlowMapperEventRecord(
                    sessionState.sessionId,
                    ScheduleCleanup(expiryTime)
                )
            }
    }

    private fun postProcessPendingPlatformError(context: FlowEventContext<Any>) {
        /**
         * If a platform error was previously reported to the user the error should now be cleared. If we have reached
         * the post-processing step we can assume the pending error has been processed.
         */
        context.checkpoint.clearPendingPlatformError()
    }

    /**
     * Check to see if any external events needs to be sent or resent.
     */
    private fun getExternalEvent(context: FlowEventContext<Any>, now: Instant): List<Record<*, *>> {
        val externalEventState = context.checkpoint.externalEventState
        return if (externalEventState == null) {
            listOf()
        } else {
            val retryWindow = context.flowConfig.getLong(EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW)
            externalEventManager.getEventToSend(externalEventState, now, Duration.ofMillis(retryWindow))
                .let { (updatedExternalEventState, record) ->
                    context.checkpoint.externalEventState = updatedExternalEventState
                    if (record != null) {
                        listOf(record)
                    } else {
                        listOf()
                    }
                }
        }
    }

    private fun postProcessRetries(context: FlowEventContext<Any>): List<Record<FlowKey, FlowStatus>> {
        /**
         * When the flow enters a retry state the flow status is updated to "RETRYING", this
         * needs to be set back when a retry clears, however we only need to do this if the flow
         * is still running, if it is now complete the status will have been updated already
         */

        val checkpoint = context.checkpoint

        // The flow was not in a retry state so nothing to do
        if (!checkpoint.inRetryState) {
            return listOf()
        }

        // If we reach the post-processing step with a retry set we
        // assume whatever the previous retry was it has now cleared
        log.debug("The Flow was in a retry state that has now cleared.")
        checkpoint.markRetrySuccess()

        // If the flow has been completed, no need to update the status
        if (!checkpoint.doesExist) {
            return listOf()
        }

        val status = flowMessageFactory.createFlowStartedStatusMessage(checkpoint)
        return listOf(flowRecordFactory.createFlowStatusRecord(status))
    }

    private fun getStateMetadata(context: FlowEventContext<Any>): Metadata? {
        val checkpoint = context.checkpoint
        // Find the earliest expiry time for any open sessions.
        val lastReceivedMessageTime = checkpoint.sessions.filter {
            it.status == SessionStateType.CREATED || it.status == SessionStateType.CONFIRMED
        }.minByOrNull { it.lastReceivedMessageTime }?.lastReceivedMessageTime

        return if (lastReceivedMessageTime != null) {
            // Add the metadata key if there are any open sessions.
            val expiryTime = lastReceivedMessageTime + Duration.ofMillis(
                context.flowConfig.getLong(SESSION_TIMEOUT_WINDOW)
            )
            val newMap = mapOf(STATE_META_SESSION_EXPIRY_KEY to expiryTime.epochSecond)
            context.metadata?.let {
                Metadata(it + newMap)
            } ?: Metadata(newMap)
        } else {
            // If there are no open sessions, remove the metadata key.
            context.metadata?.let {
                Metadata(it - STATE_META_SESSION_EXPIRY_KEY)
            }
        }
    }
}
