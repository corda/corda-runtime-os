@file:JvmName("Constants")
package net.corda.example.vnode

import co.paralleluniverse.fibers.instrument.QuasarInstrumentor
import com.sun.management.HotSpotDiagnosticMXBean
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.lang.management.ManagementFactory
import java.time.Duration.ofSeconds
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.messaging.api.records.Record
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.FlowConfig.PROCESSING_FLOW_CLEANUP_TIME
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS
import net.corda.schema.configuration.FlowConfig.SESSION_FLOW_CLEANUP_TIME
import net.corda.schema.configuration.FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW
import net.corda.schema.configuration.FlowConfig.SESSION_MESSAGE_RESEND_WINDOW
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.framework.BundleReference
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC
import org.slf4j.LoggerFactory

const val VNODE_SERVICE = "vnode"
const val SHUTDOWN_GRACE = 30L

@Suppress("unused", "LongParameterList")
@Component(reference = [
    Reference(
        name = VNODE_SERVICE,
        service = VNodeService::class,
        cardinality = OPTIONAL,
        policy = DYNAMIC
    )
])
class CordaVNode @Activate constructor(
    @Reference
    private val flowEventProcessorFactory: FlowEventProcessorFactory,

    @Reference
    private val shutdown: Shutdown,

    @Reference(cardinality = OPTIONAL)
    private val quasar: QuasarInstrumentor?,

    private val componentContext: ComponentContext
) : Application {
    private companion object {
        private const val EXAMPLE_CPI_RESOURCE = "META-INF/example-cpi-package.cpb"
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        private const val TIMEOUT_MILLIS = 1000L
        private const val WAIT_MILLIS = 100L
        private val ONE_SECOND = ofSeconds(1)

        private val smartConfig: SmartConfig

        init {
            val configFactory = SmartConfigFactory.createWithoutSecurityServices()

            val config = ConfigFactory.empty()
                .withValue(PROCESSING_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(5000L))
                .withValue(PROCESSING_MAX_RETRY_ATTEMPTS, ConfigValueFactory.fromAnyRef(5))
                .withValue(PROCESSING_MAX_FLOW_SLEEP_DURATION, ConfigValueFactory.fromAnyRef(5000L))
                .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(60000L))
                .withValue(SESSION_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(5000L))
                .withValue(SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
                .withValue(SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
                .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(972800))
            smartConfig = configFactory.create(config)
        }
    }

    private val appName: String = System.getProperty("app.name", "heap")
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val counter = AtomicInteger()
    private val mxBean = ManagementFactory.newPlatformMXBeanProxy(
        ManagementFactory.getPlatformMBeanServer(),
        "com.sun.management:type=HotSpotDiagnostic",
        HotSpotDiagnosticMXBean::class.java
    )

    private val vnode: VNodeService = fetchService(VNODE_SERVICE, TIMEOUT_MILLIS)
    private val application = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Application")
    }

    private inline fun <reified T> fetchService(name: String, timeout: Long): T {
        return fetchService(name, T::class.java, timeout)
    }

    private fun <T> fetchService(name: String, serviceType: Class<T>, timeout: Long): T {
        var remainingMillis = timeout.coerceAtLeast(0)
        while (true) {
            componentContext.locateService<T>(name)?.also { svc ->
                return svc
            }
            if (remainingMillis <= 0) {
                break
            }
            val waitMillis = remainingMillis.coerceAtMost(WAIT_MILLIS)
            Thread.sleep(waitMillis)
            remainingMillis -= waitMillis
        }
        throw TimeoutException("Service $serviceType did not arrive in $timeout milliseconds")
    }

    private fun dumpHeap(tag: String) {
        val dumpFileName = "$appName-${Instant.now().epochSecond}-${counter.incrementAndGet()}-$tag.hprof"
        mxBean.dumpHeap(dumpFileName, true)
    }

    private fun generateRandomId(): String = UUID.randomUUID().toString()

    private fun createRPCStartFlow(clientId: String, virtualNodeInfo: VirtualNodeInfo): StartFlow {
        return StartFlow(
            FlowStartContext(
                FlowKey(clientId, virtualNodeInfo.holdingIdentity),
                FlowInitiatorType.RPC,
                clientId,
                virtualNodeInfo.holdingIdentity,
                virtualNodeInfo.cpiIdentifier.name,
                virtualNodeInfo.holdingIdentity,
                "com.example.cpk.ExampleFlow",
                "{\"message\":\"Bongo!\"}",
                emptyKeyValuePairList(),
                Instant.now(),
            ), "{\"message\":\"Bongo!\"}"
        )
    }

    @Suppress("SameParameterValue")
    private fun executeSandbox(clientId: String, resourceName: String) {
        val holdingIdentity = HoldingIdentity(MemberX500Name.parse(X500_NAME), generateRandomId())
        val vnodeInfo = vnode.loadVirtualNode(resourceName, holdingIdentity)
        try {
            // Checkpoint: We have loaded the CPI into the framework.
            logger.info("Loaded CPI: {}", vnodeInfo.cpiIdentifier)
            dumpHeap("loaded")

            // Checkpoint: We have created a sandbox for this CPI.
            val sandboxContext = vnode.getOrCreateSandbox(holdingIdentity, vnodeInfo.cpiIdentifier)
            logger.info("Created sandbox: {}", sandboxContext.sandboxGroup.metadata.values.map(CpkMetadata::cpkId))
            dumpHeap("created")

            val rpcStartFlow = createRPCStartFlow(clientId, vnodeInfo.toAvro())
            val flowId = generateRandomId()
            val record = Record(FLOW_EVENT_TOPIC, flowId, FlowEvent(flowId, rpcStartFlow))
            flowEventProcessorFactory.create(smartConfig).apply {
                val result = onNext(null, record)
                result.responseEvents.singleOrNull { evt ->
                    evt.topic == FLOW_EVENT_TOPIC
                }?.also { evt ->
                    @Suppress("unchecked_cast")
                    onNext(result.updatedState, evt as Record<String, FlowEvent>)
                }
            }
        } finally {
            val completion = vnode.flushSandboxCache()
            do {
                @Suppress("ExplicitGarbageCollectionCall")
                System.gc()
            } while (!vnode.waitForSandboxCache(completion, ONE_SECOND))

            // Checkpoint: We have destroyed the sandbox.
            dumpHeap("destroyed")
            logger.info("Destroyed sandbox")

            vnode.unloadVirtualNode(vnodeInfo)
            logger.info("Unloaded CPI")
            dumpHeap("unloaded")
        }
    }

    @Deactivate
    fun terminate() {
        logger.info("Terminated")
    }

    override fun startup(args: Array<String>) {
        application.submit(::process)
    }

    @Suppress("NestedBlockDepth")
    private fun process() {
        logger.info("Starting")
        try {
            dumpHeap("started")
            executeSandbox("client-1", EXAMPLE_CPI_RESOURCE)
            executeSandbox("client-2", EXAMPLE_CPI_RESOURCE)
            executeSandbox("client-3", EXAMPLE_CPI_RESOURCE)
        } catch (e: Exception) {
            logger.error("Application error", e)
        } finally {
            dumpHeap("finished")
            shutdown.shutdown(componentContext.usingBundle)

            quasar?.also { instrumentor ->
                logger.info("Instrumentor: {}", instrumentor::class.java)
                val dbField = instrumentor::class.java.getDeclaredField("dbForClassloader").apply {
                    isAccessible = true
                }

                @Suppress("unchecked_cast")
                val classLoaders = (dbField.get(instrumentor) as Map<ClassLoader, *>).keys
                for (classLoader in classLoaders) {
                    if (classLoader is BundleReference) {
                        val bundle = classLoader.bundle
                        if (bundle == null) {
                            logger.info("CLASSLOADER>> {} is DEAD", classLoader)
                        } else if (bundle.location.startsWith("FLOW/")) {
                            logger.info("BUNDLE>> {} is-in-use={} (classloader={})",
                                bundle, bundle.adapt(BundleWiring::class.java)?.isInUse, classLoader::class.java)
                        }
                    }
                }
            }
        }
    }

    override fun shutdown() {
        logger.info("Terminating")
        with(application) {
            shutdown()
            if (!awaitTermination(SHUTDOWN_GRACE, SECONDS)) {
                shutdownNow()
            }
        }
        dumpHeap("shutdown")
    }
}
