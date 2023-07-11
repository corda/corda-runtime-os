package net.corda.testing.driver.sandbox

import java.time.Duration
import java.util.Collections.unmodifiableSet
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_FILTER
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.annotations.Reference

interface VirtualNodeService {
    fun loadVirtualNode(name: MemberX500Name, resourceName: String): VirtualNodeInfo
    fun releaseVirtualNode(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>?
    fun unloadVirtualNode(completion: CompletableFuture<*>)

    fun loadSystemNodes(names: Set<MemberX500Name>, resourceName: String): List<VirtualNodeInfo>

    fun getLocalMembers(): Set<MemberX500Name>
}

@Suppress("unused")
@Component(
    service = [ VirtualNodeService::class ],
    configurationPid = [ CORDA_LOCAL_IDENTITY_PID ],
    configurationPolicy = REQUIRE,
    property = [ DRIVER_SERVICE ]
)
class VirtualNodeServiceImpl @Activate constructor(
    @Reference
    private val virtualNodeLoader: VirtualNodeLoader,

    @Reference(target = DRIVER_SERVICE_FILTER)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,

    properties: Map<String, Any?>
) : VirtualNodeService {
    private companion object {
        private const val DEFAULT_SANDBOX_CACHE_SIZE = 5L
        private val ONE_SECOND = Duration.ofSeconds(1)
    }

    private val localMemberNames: Set<MemberX500Name>
    private val cpiGroups = mutableMapOf<String, String>()

    init {
        sandboxGroupContextComponent.resizeCaches(DEFAULT_SANDBOX_CACHE_SIZE)

        val members = mutableSetOf<MemberX500Name>()
        (properties[CORDA_MEMBER_COUNT] as? Int)?.let { localCount ->
            for (idx in 0 until localCount) {
                (properties["$CORDA_MEMBER_X500_NAME.$idx"] as? String)
                    ?.let(MemberX500Name::parse)
                    ?.also(members::add)
            }
        }
        localMemberNames = unmodifiableSet(members)
    }

    private fun generateHoldingIdentity(memberName: MemberX500Name, resourceName: String): HoldingIdentity {
        val groupId = cpiGroups.computeIfAbsent(resourceName) {
            UUID.randomUUID().toString()
        }
        return HoldingIdentity(memberName, groupId)
    }

    override fun getLocalMembers(): Set<MemberX500Name> {
        return localMemberNames
    }

    override fun loadSystemNodes(names: Set<MemberX500Name>, resourceName: String): List<VirtualNodeInfo> {
        return virtualNodeLoader.loadSystemNode(resourceName, names)
    }

    override fun loadVirtualNode(name: MemberX500Name, resourceName: String): VirtualNodeInfo {
        return virtualNodeLoader.loadVirtualNode(resourceName, generateHoldingIdentity(name, resourceName))
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
