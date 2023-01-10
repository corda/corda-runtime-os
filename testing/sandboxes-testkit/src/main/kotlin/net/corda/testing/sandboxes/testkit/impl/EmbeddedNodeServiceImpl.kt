package net.corda.testing.sandboxes.testkit.impl

import java.net.URL
import java.nio.file.Path
import java.security.KeyPair
import java.security.PublicKey
import net.corda.data.virtualnode.VirtualNodeInfo as AvroVirtualNodeInfo
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.embedded.EmbeddedNodeService
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class EmbeddedNodeServiceImpl @Activate constructor(
    @Reference
    private val sandboxSetup: SandboxSetup,
    private val bundleContext: BundleContext
) : EmbeddedNodeService {
    private lateinit var virtualNodeService: VirtualNodeService
    private val localMembers = linkedSetOf<MemberX500Name>()

    override fun configure(baseDirectory: Path, timeout: Long) {
        sandboxSetup.configure(bundleContext, baseDirectory)
        virtualNodeService = sandboxSetup.fetchService(timeout)
        if (localMembers.isNotEmpty()) {
            (virtualNodeService as? LocalMembership)?.setLocalMembers(localMembers)
        }
    }

    override fun loadVirtualNodes(fileURL: URL): Set<AvroVirtualNodeInfo> {
        return virtualNodeService.loadVirtualNodes(fileURL.toString()).mapTo(linkedSetOf(), VirtualNodeInfo::toAvro)
    }

    override fun setMembershipGroup(network: Map<MemberX500Name, PublicKey>) {
        sandboxSetup.setMembershipGroup(network)
    }

    override fun setLocalIdentities(localMembers: Set<MemberX500Name>, localKeys: Map<MemberX500Name, KeyPair>) {
        sandboxSetup.setLocalIdentities(localMembers, localKeys)
        this.localMembers += localMembers
    }

    override fun configureLocalTenants(timeout: Long) {
        sandboxSetup.configureLocalTenants(timeout)
    }
}
