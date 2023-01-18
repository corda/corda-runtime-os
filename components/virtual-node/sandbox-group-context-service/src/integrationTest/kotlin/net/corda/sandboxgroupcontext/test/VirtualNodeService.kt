package net.corda.sandboxgroupcontext.test

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.registerCordappCustomSerializers
import net.corda.sandboxgroupcontext.service.registerCustomCryptography
import net.corda.sandboxgroupcontext.service.registerNotaryPluginProviders
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.SubFlow
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.fail
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.time.Duration.ofSeconds
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer

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
        private val ONE_SECOND = ofSeconds(1)

        private fun generateHoldingIdentity() = createTestHoldingIdentity(X500_NAME, UUID.randomUUID().toString())
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

    private fun getOrCreateSandbox(virtualNodeInfo: VirtualNodeInfo, type: SandboxGroupType): SandboxGroupContext {
        val cpi = cpiLoader.getCpiMetadata(virtualNodeInfo.cpiIdentifier).get()
            ?: fail("CPI ${virtualNodeInfo.cpiIdentifier} not found")
        val cpks = when (type) {
            SandboxGroupType.FLOW -> cpi.cpksMetadata
            else -> cpi.contractCpksMetadata()
        }
        val vNodeContext = VirtualNodeContext(
            virtualNodeInfo.holdingIdentity,
            cpks.mapTo(LinkedHashSet(), CpkMetadata::fileChecksum),
            type,
            null
        )
        return sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            val closeables = listOf(
                sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext),
                sandboxGroupContextComponent.registerCordappCustomSerializers(sandboxGroupContext),
                sandboxGroupContextComponent.registerNotaryPluginProviders(sandboxGroupContext)
            )
            sandboxGroupContextComponent.acceptCustomMetadata(sandboxGroupContext)
            AutoCloseable {
                closeables.forEach(AutoCloseable::close)
            }
        }
    }

    fun loadSandbox(resourceName: String, type: SandboxGroupType): SandboxGroupContext {
        val vnodeInfo = virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity())
        return getOrCreateSandbox(vnodeInfo, type).also { ctx ->
            vnodes[ctx] = vnodeInfo
        }
    }

    /**
     * Test purposes only.
     * In real world scenarios, [SandboxGroupContext] instances cannot be unloaded while still in use and there should
     * be no public API methods allowing users to manually unload them either.
     */
    fun releaseSandbox(sandboxGroupContext: SandboxGroupContext): CompletableFuture<*>? {
        vnodes.remove(sandboxGroupContext)?.also(virtualNodeLoader::unloadVirtualNode)
        return sandboxGroupContextComponent.remove(sandboxGroupContext.virtualNodeContext)
    }

    /**
     * Wait for a sandbox to be garbage collected. Ensure that invoking test can time out,
     * because the sandbox cannot be collected if it is still referenced somewhere.
     */
    fun unloadSandbox(completion: CompletableFuture<*>) {
        do {
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
        } while (!sandboxGroupContextComponent.waitFor(completion, ONE_SECOND))
    }

    fun withSandbox(resourceName: String, type: SandboxGroupType, action: BiConsumer<VirtualNodeService, SandboxGroupContext>) {
        val sandboxGroupContext = loadSandbox(resourceName, type)
        try {
            action.accept(this, sandboxGroupContext)
        } finally {
            releaseSandbox(sandboxGroupContext)
        }
    }

    fun getFlowClass(className: String, groupContext: SandboxGroupContext): Class<out Flow> {
        return groupContext.sandboxGroup.loadClassFromMainBundles(className, Flow::class.java)
    }

    fun getBundleContext(clazz: Class<*>): BundleContext {
        return (FrameworkUtil.getBundle(clazz) ?: fail("$clazz has no bundle")).bundleContext
    }

    fun <T : Any> runFlow(workflowClass: Class<out Flow>): T {
        val context = getBundleContext(workflowClass)
        val reference = context.getServiceReferences(SubFlow::class.java, "(component.name=${workflowClass.name})")
            .maxOrNull() ?: fail("No service found for ${workflowClass.name}.")
        return context.getService(reference)?.let { service ->
            try {
                @Suppress("unchecked_cast")
                service.call() as? T ?: fail("Workflow did not return the correct type.")
            } finally {
                context.ungetService(reference)
            }
        } ?: fail("${workflowClass.name} service not available - OSGi error?")
    }

    fun <T : Any> runFlow(className: String, groupContext: SandboxGroupContext): T {
        return runFlow(getFlowClass(className, groupContext))
    }
}
