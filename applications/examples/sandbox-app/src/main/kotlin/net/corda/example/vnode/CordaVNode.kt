@file:JvmName("Constants")
package net.corda.example.vnode

import co.paralleluniverse.fibers.instrument.Retransform
import com.sun.management.HotSpotDiagnosticMXBean
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.messaging.api.records.Record
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.securitymanager.SecurityManagerService
import net.corda.v5.base.util.loggerFor
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.framework.AdminPermission
import org.osgi.framework.BundleReference
import org.osgi.framework.PackagePermission
import org.osgi.framework.PackagePermission.EXPORTONLY
import org.osgi.framework.PackagePermission.IMPORT
import org.osgi.framework.ServicePermission
import org.osgi.framework.ServicePermission.GET
import org.osgi.framework.ServicePermission.REGISTER
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC
import org.osgi.service.permissionadmin.PermissionAdmin
import java.io.FilePermission
import java.lang.management.ManagementFactory
import java.lang.management.ManagementPermission
import java.lang.reflect.ReflectPermission
import java.net.NetPermission
import java.net.SocketPermission
import java.nio.file.LinkPermission
import java.time.Instant
import java.util.PropertyPermission
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

const val VNODE_SERVICE = "vnode"

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
    private val securityManager: SecurityManagerService,

    @Reference
    private val shutdown: Shutdown,

    private val componentContext: ComponentContext
) : Application {
    private companion object {
        private const val EXAMPLE_CPI_RESOURCE = "META-INF/example-cpi-package.cpb"
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        private const val TIMEOUT_MILLIS = 1000L
        private const val WAIT_MILLIS = 100L
    }

    private val logger = loggerFor<CordaVNode>()

    private val counter = AtomicInteger()
    private val mxBean = ManagementFactory.newPlatformMXBeanProxy(
        ManagementFactory.getPlatformMBeanServer(),
        "com.sun.management:type=HotSpotDiagnostic",
        HotSpotDiagnosticMXBean::class.java
    )

    private val vnode: VNodeService = fetchService(VNODE_SERVICE, TIMEOUT_MILLIS)

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
        val dumpFileName = "heap-${Instant.now().epochSecond}-${counter.incrementAndGet()}-$tag.hprof"
        mxBean.dumpHeap(dumpFileName, true)
    }

    private fun generateRandomId(): String = UUID.randomUUID().toString()

    private fun createRPCStartFlow(clientId: String, virtualNodeInfo: VirtualNodeInfo): StartFlow {
        return StartFlow(
            FlowStartContext(
                FlowStatusKey(clientId, virtualNodeInfo.holdingIdentity),
                FlowInitiatorType.RPC,
                clientId,
                virtualNodeInfo.holdingIdentity,
                virtualNodeInfo.cpiIdentifier.name,
                virtualNodeInfo.holdingIdentity,
                "com.example.cpk.ExampleFlow",
                Instant.now(),
            ),  "{\"message\":\"Bongo!\"}"
        )
    }

    @Suppress("SameParameterValue")
    private fun executeSandbox(clientId: String, resourceName: String) {
        val holdingIdentity = HoldingIdentity(X500_NAME, generateRandomId())
        val vnodeInfo = vnode.loadVirtualNode(resourceName, holdingIdentity)
        try {
            // Checkpoint: We have loaded the CPI into the framework.
            logger.info("Loaded CPI: {}", vnodeInfo.cpiIdentifier)
            dumpHeap("loaded")

            // Checkpoint: We have created a sandbox for this CPI.
            val sandboxContext = vnode.getOrCreateSandbox(holdingIdentity)
            try {
                logger.info("Created sandbox: {}", sandboxContext.sandboxGroup.metadata.values.map {it.id})
                dumpHeap("created")

                val rpcStartFlow = createRPCStartFlow(clientId, vnodeInfo.toAvro())
                val flowKey = FlowKey(generateRandomId(), holdingIdentity.toAvro())
                val record = Record(FLOW_EVENT_TOPIC, flowKey, FlowEvent(flowKey, rpcStartFlow))
                flowEventProcessorFactory.create().apply {
                    val result = onNext(null, record)
                    result.responseEvents.singleOrNull { evt ->
                        evt.topic == FLOW_EVENT_TOPIC
                    }?.also { evt ->
                        @Suppress("unchecked_cast")
                        onNext(result.updatedState, evt as Record<FlowKey, FlowEvent>)
                    }
                }
            } finally {
                (sandboxContext as AutoCloseable).close()
            }

            // Checkpoint: We have destroyed the sandbox.
            logger.info("Destroyed sandbox")
            dumpHeap("destroyed")
        } finally {
            vnode.unloadVirtualNode(vnodeInfo)
        }
        logger.info("Unloaded CPI")
    }

    @Suppress("NestedBlockDepth")
    override fun startup(args: Array<String>) {
        logger.info("Starting")
        try {
            securityManager.start()
            securityManager.denyPermissions("FLOW/*", listOf(
                // OSGi permissions.
                AdminPermission(),
                ServicePermission("*", GET),
                PackagePermission("net.corda", "$EXPORTONLY,$IMPORT"),
                PackagePermission("net.corda.*", "$EXPORTONLY,$IMPORT"),

                // Prevent the FLOW sandboxes from importing these packages,
                // which effectively forbids them from executing most OSGi code.
                PackagePermission("org.osgi.framework", IMPORT),
                PackagePermission("org.osgi.service.component", IMPORT),

                // Java permissions.
                RuntimePermission("*"),
                ReflectPermission("*"),
                NetPermission("*"),
                LinkPermission("hard"),
                LinkPermission("symbolic"),
                ManagementPermission("control"),
                ManagementPermission("monitor"),
                PropertyPermission("*", "read,write"),
                SocketPermission("*", "accept,connect,listen"),
                FilePermission("<<ALL FILES>>", "read,write,execute,delete,readlink")
            ))
            securityManager.grantPermissions("FLOW/*", listOf(
                PackagePermission("net.corda.v5.*", IMPORT),
                ServicePermission("(location=FLOW/*)", GET),
                ServicePermission("net.corda.v5.*", GET)
            ))
            securityManager.denyPermissions("*", listOf(
                ServicePermission(PermissionAdmin::class.java.name, REGISTER)
            ))

            dumpHeap("started")
            executeSandbox("client-1", EXAMPLE_CPI_RESOURCE)
            executeSandbox("client-2", EXAMPLE_CPI_RESOURCE)
            executeSandbox("client-3", EXAMPLE_CPI_RESOURCE)
        } finally {
            dumpHeap("finished")
            shutdown.shutdown(componentContext.usingBundle)

            val instrumentor = Retransform.getInstrumentor()
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

    override fun shutdown() {
        logger.info("Terminating")
        dumpHeap("shutdown")
    }
}
