package net.corda.testing.sandboxes.groupcontext

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.fail
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Component(service = [ VirtualNodeService::class ])
class VirtualNodeService @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,

    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference
    private val sandboxGroupContextComponent: SandboxGroupContextComponent
) {
    private companion object {
        private const val X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"

        private fun generateHoldingIdentity() = HoldingIdentity(X500_NAME, UUID.randomUUID().toString())
    }
    init {
        // setting cache size to 2 as some tests require 2 concurrent sandboxes for validating they don't overlap
        sandboxGroupContextComponent.initCache(2)
    }

    private val vnodes = mutableMapOf<SandboxGroupContext, VirtualNodeInfo>()

    @Suppress("unused")
    @Deactivate
    fun done() {
        sandboxGroupContextComponent.close()
    }

    private fun getOrCreateSandbox(virtualNodeInfo: VirtualNodeInfo): SandboxGroupContext {
        val cpi = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: fail("CPI ${virtualNodeInfo.cpiIdentifier} not found")
        val vNodeContext = VirtualNodeContext(
            virtualNodeInfo.holdingIdentity,
            cpi.cpksMetadata.mapTo(LinkedHashSet(), CpkMetadata::cpkId),
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )
        return sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)
        }
    }

    fun loadSandbox(resourceName: String): SandboxGroupContext {
        val vnodeInfo = virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())
        return getOrCreateSandbox(vnodeInfo).also { ctx ->
            vnodes[ctx] = vnodeInfo
        }
    }

    fun unloadSandbox(sandboxGroupContext: SandboxGroupContext) {
        (sandboxGroupContext as? AutoCloseable)?.close()
        vnodes.remove(sandboxGroupContext)?.let(virtualNodeLoader::unloadVirtualNode)
    }

    fun <T : Any> runFlow(className: String, groupContext: SandboxGroupContext, rpcData: RPCRequestData? = null): T {
        val workflowClass = groupContext.sandboxGroup.loadClassFromMainBundles(className, Flow::class.java)
        val context = FrameworkUtil.getBundle(workflowClass).bundleContext
            ?: throw RuntimeException("Couldn't load bundleContext.")

        return runSubFlow(context, className)
            ?: runRPCFlow(context, className, rpcData)
            ?: fail("$className service not available - OSGi error?")
    }

    private fun <T> runSubFlow(context: BundleContext, className: String): T? {
        val reference = context.getServiceReferences(SubFlow::class.java, "(component.name=$className)").firstOrNull()
        if (reference == null) {
            println("No SubFlow service found for $className.")
            return null
        } else {
            return context.getService(reference)?.let { service ->
                try {
                    @Suppress("unchecked_cast")
                    return service.call() as? T ?: fail("Workflow did not return the correct type.")
                } finally {
                    context.ungetService(reference)
                }
            }
        }
    }

    private fun <T> runRPCFlow(context: BundleContext, className: String, rpcData: RPCRequestData?): T? {
        val reference = context.getServiceReferences(RPCStartableFlow::class.java, null).firstOrNull()
        if (reference == null) {
            println("No RPCStartableFlow service found for $className.")
            return null
        } else {
            val requestData = rpcData ?: fail("Tried to start an RPCStartableFlow with rpcData = null.")

            return context.getService(reference)?.let { service ->
                try {
                    @Suppress("unchecked_cast")
                    return service.call(requestData) as? T ?: fail("Workflow did not return the correct type.")
                } finally {
                    context.ungetService(reference)
                }
            }
        }
    }
}
