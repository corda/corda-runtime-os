package net.corda.testing.driver.sandbox

import java.security.PublicKey
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(
    service = [ LocallyHostedIdentitiesService::class ],
    property = [ DRIVER_SERVICE ]
)
@ServiceRanking(DRIVER_SERVICE_RANKING)
class LocallyHostedIdentitiesServiceImpl : LocallyHostedIdentitiesService {
    private companion object {
        private val DUMMY_SESSION_KEY = object : PublicKey {
            override fun getAlgorithm(): String = "None"
            override fun getFormat(): String = "None"
            override fun getEncoded(): ByteArray = byteArrayOf()
        }
    }

    private val identities = mutableMapOf<HoldingIdentity, IdentityInfo>()

    override fun getIdentityInfo(identity: HoldingIdentity): IdentityInfo {
        return identities.computeIfAbsent(identity) { hid ->
            IdentityInfo(hid, emptyList(), DUMMY_SESSION_KEY)
        }
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
    }

    override fun stop() {
    }
}
