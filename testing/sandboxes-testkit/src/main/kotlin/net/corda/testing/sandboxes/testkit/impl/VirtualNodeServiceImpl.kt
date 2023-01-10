package net.corda.testing.sandboxes.testkit.impl

import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.VirtualNodeLoader
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture
import java.util.UUID

@Suppress("unused")
@Component(service = [ VirtualNodeService::class ])
class VirtualNodeServiceImpl @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,

    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference
    private val sandboxGroupContextComponent: SandboxGroupContextComponent
) : VirtualNodeService, LocalMembership {
    private companion object {
        private const val DEFAULT_X500_NAME = "CN=Testing, OU=Application, O=R3, L=London, C=GB"
        private val ONE_SECOND = ofSeconds(1)
    }

    private val localMemberNames = linkedSetOf(MemberX500Name.parse(DEFAULT_X500_NAME))
    private val cpiGroups = mutableMapOf<String, String>()

    init {
        sandboxGroupContextComponent.initCaches(1)
    }

    private fun generateHoldingIdentity(memberName: MemberX500Name, resourceName: String): HoldingIdentity {
        val groupId = cpiGroups.computeIfAbsent(resourceName) {
            UUID.randomUUID().toString()
        }
        return HoldingIdentity(memberName, groupId)
    }

    override fun setLocalMembers(localMembers: Set<MemberX500Name>) {
        synchronized(localMemberNames) {
            localMemberNames.clear()
            localMemberNames += localMembers
        }
    }

    override fun getLocalMembers(): Set<MemberX500Name> {
        return synchronized(localMemberNames) {
            LinkedHashSet(localMemberNames)
        }
    }

    override fun loadVirtualNodes(resourceName: String): Set<VirtualNodeInfo> {
        return getLocalMembers().mapTo(linkedSetOf()) { memberName ->
            virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity(memberName, resourceName))
        }
    }

    override fun loadVirtualNode(resourceName: String): VirtualNodeInfo {
        val localMemberName = synchronized(localMemberNames) {
            localMemberNames.single()
        }
        return virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity(localMemberName, resourceName))
    }

    override fun releaseVirtualNode(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? {
        return sandboxGroupContextComponent.remove(virtualNodeContext)
    }

    /**
     * Wait for a sandbox to be garbage collected. Ensure that invoking test can time out,
     * because the sandbox cannot be collected if it is still referenced somewhere.
     */
    override fun unloadVirtualNode(completion: CompletableFuture<*>) {
        do {
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
        } while (!sandboxGroupContextComponent.waitFor(completion, ONE_SECOND))
    }
}
