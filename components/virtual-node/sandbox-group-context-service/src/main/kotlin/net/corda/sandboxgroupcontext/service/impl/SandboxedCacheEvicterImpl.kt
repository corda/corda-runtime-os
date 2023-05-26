package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.SandboxedCache
import net.corda.sandboxgroupcontext.SandboxedCacheEvicter
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.utilities.debug
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [SandboxedCacheEvicter::class])
class SandboxedCacheEvicterImpl @Activate constructor(
    private val bundleContext: BundleContext,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent
): SandboxedCacheEvicter {

    private companion object {
        private const val NON_PROTOTYPE_SERVICES = "(!(${Constants.SERVICE_SCOPE}=${Constants.SCOPE_PROTOTYPE}))"
        val log: Logger = LoggerFactory.getLogger(SandboxedCacheEvicterImpl::class.java)
    }

    private var sandboxGroupType: SandboxGroupType? = null

    override fun setSandboxGroupType(sandboxGroupType: SandboxGroupType) {
        require(this.sandboxGroupType == null) { "The sandbox group type cannot be set multiple times" }
        this.sandboxGroupType = sandboxGroupType
        if (!sandboxGroupContextComponent.addEvictionListener(sandboxGroupType, ::onEviction)) {
            log.error("FAILED TO ADD EVICTION LISTENER")
        }
    }

    @Suppress("unused")
    @Deactivate
    fun shutdown() {
        val sandboxGroupType = requireNotNull(this.sandboxGroupType) { "The sandbox group type has not been set when evicting" }
        if (!sandboxGroupContextComponent.removeEvictionListener(sandboxGroupType, ::onEviction)) {
            log.error("FAILED TO REMOVE EVICTION LISTENER")
        }
    }

    private fun onEviction(vnc: VirtualNodeContext) {
        log.debug("Sandbox {} has been evicted", vnc)
        for (ref in bundleContext.getServiceReferences(SandboxedCache::class.java, NON_PROTOTYPE_SERVICES)) {
            bundleContext.getService(ref)?.also { cache ->
                log.debug {
                    "Evicting cached items from ${cache::class.java} with holding identity: ${vnc.holdingIdentity} and sandbox type: " +
                            vnc.sandboxGroupType
                }
                cache.remove(vnc.holdingIdentity, vnc.sandboxGroupType)
            }
        }
    }
}