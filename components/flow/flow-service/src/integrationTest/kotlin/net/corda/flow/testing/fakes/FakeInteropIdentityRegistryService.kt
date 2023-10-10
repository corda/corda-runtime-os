package net.corda.flow.testing.fakes

import java.util.*
import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.registry.InteropIdentityRegistryService
import net.corda.interop.identity.registry.InteropIdentityRegistryView
import net.corda.v5.application.interop.facade.FacadeId
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

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

        override fun getIdentitiesByGroupId(groupId: UUID): Set<InteropIdentity> {
            TODO("Not yet implemented")
        }

        override fun getIdentityWithShortHash(shortHash: ShortHash): InteropIdentity? {
            TODO("Not yet implemented")
        }

        override fun getIdentitiesByApplicationName(applicationName: String): Set<InteropIdentity> {
            TODO("Not yet implemented")
        }

        override fun getIdentityWithApplicationName(applicationName: String): InteropIdentity? {
            TODO("Not yet implemented")
        }

        override fun getIdentitiesByFacadeId(facadeId: FacadeId): Set<InteropIdentity> {
            TODO("Not yet implemented")
        }

        override fun getOwnedIdentities(groupId: UUID): Set<InteropIdentity> {
            TODO("Not yet implemented")
        }

        override fun getOwnedIdentity(groupId: UUID): InteropIdentity? {
            TODO("Not yet implemented")
        }
    }
}
