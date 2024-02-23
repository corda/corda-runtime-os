package net.corda.flow.pipeline.runner.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.data.flow.state.waiting.start.WaitingForStartFlow
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.toMap
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_REQUIRE_CLOSE
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_TIMEOUT_MS
import net.corda.tracing.TraceTag
import net.corda.tracing.trace
import net.corda.v5.application.flows.FlowContextPropertyKeys
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Suppress("LongParameterList")
@Component(service = [FlowRunner::class])
class FlowRunnerImpl @Activate constructor(
    @Reference(service = FlowFiberFactory::class)
    private val flowFiberFactory: FlowFiberFactory,
    @Reference(service = FlowFactory::class)
    private val flowFactory: FlowFactory,
    @Reference(service = FlowFiberExecutionContextFactory::class)
    private val flowFiberExecutionContextFactory: FlowFiberExecutionContextFactory,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
) : FlowRunner {
    override fun runFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): FiberFuture {
        if (context.checkpoint.initialPlatformVersion != platformInfoProvider.localWorkerPlatformVersion) {
            throw FlowFatalException(
                "Platform has been modified from version " +
                    "${context.checkpoint.initialPlatformVersion} to version " +
                    "${platformInfoProvider.localWorkerPlatformVersion}.  The flow must be restarted."
            )
        }

        val waitingFor = context.checkpoint.waitingFor?.value
        val receivedEvent = context.inputEvent.payload
        return trace("RunFlow ${receivedEvent::class.java.name}") {
            traceTag(TraceTag.FLOW_ID, context.inputEvent.flowId)
            // TODO restore this
            //  traceTag(TraceTag.FLOW_VNODE, checkpoint.holdingIdentity.shortHash.toString())
            when (receivedEvent) {
                is StartFlow -> {
                    traceTag(TraceTag.FLOW_CLASS, receivedEvent.startContext.flowClassName)
                    traceTag(TraceTag.FLOW_INITIATOR, receivedEvent.startContext.initiatedBy.toCorda().shortHash.toString())
                    traceTag(TraceTag.FLOW_REQUEST_ID, receivedEvent.startContext.requestId)

                    if (waitingFor is WaitingForStartFlow) {
                        startFlow(context, receivedEvent)
                    } else {
                        resumeFlow(context, flowContinuation)
                    }
                }

                is SessionEvent -> {
                    val sessionInit = getInitPayload(receivedEvent)
                    if (sessionInit != null && waitingFor is WaitingForStartFlow) {
                        startInitiatedFlow(context, sessionInit, receivedEvent)
                    } else {
                        resumeFlow(context, flowContinuation)
                    }
                }

                else -> resumeFlow(context, flowContinuation)
            }
        }
    }

    private fun getInitPayload(sessionEvent: SessionEvent): SessionInit? {
        return when (val payload = sessionEvent.payload) {
            is SessionCounterpartyInfoRequest -> payload.sessionInit
            is SessionData -> payload.sessionInit
            else -> null
        }
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        startFlowEvent: StartFlow
    ): FiberFuture {

        val contextPlatformProperties = getPropertiesWithCpiMetadata(
            context.checkpoint.holdingIdentity,
            startFlowEvent.startContext.contextPlatformProperties
        )

        return startFlow(
            context,
            createFlow = { sgc -> flowFactory.createFlow(startFlowEvent, sgc) },
            updateFlowStackItem = { },
            contextUserProperties = emptyKeyValuePairList(),
            contextPlatformProperties = contextPlatformProperties
        )
    }

    private fun startInitiatedFlow(
        context: FlowEventContext<Any>,
        sessionInitEvent: SessionInit,
        sessionEvent: SessionEvent
    ): FiberFuture {
        val flowStartContext = context.checkpoint.flowStartContext
        val sessionId = sessionEvent.sessionId

        val localContext = remoteToLocalContextMapper(
            remoteUserContextProperties = sessionInitEvent.contextUserProperties,
            remotePlatformContextProperties = sessionInitEvent.contextPlatformProperties,
            mapOf("corda.account" to "account-zero")
        )

        val sessionProps = sessionEvent.contextSessionProperties.toMap()
        val requireClose = sessionProps[FLOW_SESSION_REQUIRE_CLOSE].toBoolean()
        val sessionTimeout = sessionProps[FLOW_SESSION_TIMEOUT_MS]?.let { Duration.ofMillis(it.toLong()) }

        return startFlow(
            context,
            createFlow = { sgc ->
                flowFactory.createInitiatedFlow(
                    flowStartContext,
                    requireClose,
                    sessionTimeout,
                    sgc,
                    localContext.sessionProperties
                )
            },
            updateFlowStackItem = { fsi -> addFlowStackItemSession(fsi, sessionId) },
            contextUserProperties = localContext.userProperties,
            contextPlatformProperties = getPropertiesWithCpiMetadata(
                context.checkpoint.holdingIdentity,
                localContext.platformProperties
            )
        )
    }

    private fun addFlowStackItemSession(fsi: FlowStackItem, sessionId: String) {
        fsi.sessions.add(FlowStackItemSession(sessionId, true))
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        createFlow: (SandboxGroupContext) -> FlowLogicAndArgs,
        updateFlowStackItem: (FlowStackItem) -> Unit,
        contextUserProperties: KeyValuePairList,
        contextPlatformProperties: KeyValuePairList
    ): FiberFuture {
        val checkpoint = context.checkpoint
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        val flow = createFlow(fiberContext.sandboxGroupContext)
        val stackItem = fiberContext.flowStackService.pushWithContext(
            flow = flow.logic,
            contextUserProperties = contextUserProperties,
            contextPlatformProperties = contextPlatformProperties,
            flowMetrics = context.flowMetrics
        )
        updateFlowStackItem(stackItem)
        fiberContext.sandboxGroupContext.dependencyInjector.injectServices(flow.logic)
        return flowFiberFactory.createAndStartFlowFiber(fiberContext, checkpoint.flowId, flow)
    }

    private fun resumeFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): FiberFuture {
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        return flowFiberFactory.createAndResumeFlowFiber(fiberContext, flowContinuation)
    }

    @Suppress("NestedBlockDepth")
    private fun getPropertiesWithCpiMetadata(
        holdingIdentity: HoldingIdentity,
        contextProperties: KeyValuePairList
    ) = virtualNodeInfoReadService.get(holdingIdentity)?.let {
        KeyValueStore().apply {

            // Copy other properties first so that they won't override current values
            contextProperties.items.forEach { prop ->
                this[prop.key] = prop.value
            }

            cpiInfoReadService.get(it.cpiIdentifier)?.let { metadata ->

                this[FlowContextPropertyKeys.CPI_NAME] = metadata.cpiId.name
                this[FlowContextPropertyKeys.CPI_VERSION] = metadata.cpiId.version
                this[FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH] =
                    metadata.cpiId.signerSummaryHash.toString()
                this[FlowContextPropertyKeys.CPI_FILE_CHECKSUM] = metadata.fileChecksum.toString()

                this[FlowContextPropertyKeys.INITIAL_PLATFORM_VERSION] =
                    platformInfoProvider.localWorkerPlatformVersion.toString()
                this[FlowContextPropertyKeys.INITIAL_SOFTWARE_VERSION] =
                    platformInfoProvider.localWorkerSoftwareVersion
            }
        }.avro
    } ?: contextProperties
}
