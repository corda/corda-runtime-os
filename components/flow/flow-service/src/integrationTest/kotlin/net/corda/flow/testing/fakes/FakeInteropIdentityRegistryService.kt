package net.corda.flow.testing.fakes

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.registry.InteropIdentityRegistryService
import net.corda.interop.identity.registry.InteropIdentityRegistryView
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.*

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [InteropIdentityRegistryService::class, FakeInteropIdentityRegistryService::class])
class FakeInteropIdentityRegistryService : InteropIdentityRegistryService {

    override fun getVirtualNodeRegistryView(virtualNodeShortHash: ShortHash): InteropIdentityRegistryView {
        return FakeInteropIdentityRegistryView()
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    class FakeInteropIdentityRegistryView : InteropIdentityRegistryView {
        override fun getIdentities(): Set<InteropIdentity> {
            TODO("Not yet implemented")
        }

        override fun getIdentitiesByGroupId(): Map<UUID, Set<InteropIdentity>> {
            TODO("Not yet implemented")
        }

        override fun getIdentitiesByVirtualNode(): Map<ShortHash, Set<InteropIdentity>> {
            TODO("Not yet implemented")
        }

        override fun getIdentitiesByShortHash(): Map<ShortHash, InteropIdentity> {
            TODO("Not yet implemented")
        }

        override fun getIdentitiesByApplicationName(): Map<String, InteropIdentity> {
            TODO("Not yet implemented")
        }

        override fun getIdentitiesByFacadeId(): Map<String, Set<InteropIdentity>> {
            TODO("Not yet implemented")
        }

        override fun getOwnedIdentities(): Map<UUID, InteropIdentity> {
            TODO("Not yet implemented")
        }
    }
}
