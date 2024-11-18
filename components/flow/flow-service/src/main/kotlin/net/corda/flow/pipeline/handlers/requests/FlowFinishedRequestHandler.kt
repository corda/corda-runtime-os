package net.corda.flow.pipeline.handlers.requests

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.addTerminationKeyToMeta
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.helper.getRecords
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : FlowRequestHandler<FlowIORequest.FlowFinished> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = FlowIORequest.FlowFinished::class.java

    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any>()

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowFinished): WaitingFor? {
        return null
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.FlowFinished
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint
        validateResultSize(checkpoint, request)

        val status = flowMessageFactory.createFlowCompleteStatusMessage(checkpoint, request.result)
        val records = getRecords(flowRecordFactory, context, status)

        log.info("Flow [${checkpoint.flowId}] completed successfully")
        checkpoint.markDeleted()

        context.flowMetrics.flowCompletedSuccessfully()
        val metaDataWithTermination = addTerminationKeyToMeta(context.metadata)
        return context.copy(outputRecords = context.outputRecords + records, metadata = metaDataWithTermination)
    }

    /**
     * The flow status will contain the result string. Ensure it doesn't exceed the max message size allowed.
     */
    private fun validateResultSize(checkpoint: FlowCheckpoint, request: FlowIORequest.FlowFinished) {
        val maxAllowedMessageSize = checkpoint.maxMessageSize
        val result = request.result
        if (result != null) {
            val resultSize = serializer.serialize(result)?.size
            if (resultSize != null && resultSize > maxAllowedMessageSize) {
                throw FlowFatalException(
                    "Flow attempted to return a result that is greater than the max message size allowed. Flow " +
                            "result size [$resultSize]. Max Allowed Message Size [$maxAllowedMessageSize]"
                )
            }
        }
    }
}
