package net.corda.session.mapper.service.integration

import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [LocallyHostedIdentitiesService::class])
class DummyLocallyHostedIdentitiesService @Activate constructor() : LocallyHostedIdentitiesService {

    private val identityMap = mutableMapOf<HoldingIdentity, IdentityInfo>()

    fun setIdentityInfo(identity: HoldingIdentity, identityInfo: IdentityInfo) {
        identityMap[identity] = identityInfo
    }

    override fun isHostedLocally(identity: HoldingIdentity): Boolean {
        return identity in identityMap.keys
    }

    override fun pollForIdentityInfo(identity: HoldingIdentity): IdentityInfo? {
        return identityMap[identity]
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
    }

    override fun stop() {
    }
}