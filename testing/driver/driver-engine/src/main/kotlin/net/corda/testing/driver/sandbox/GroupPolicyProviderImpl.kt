package net.corda.testing.driver.sandbox

import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_RANKING
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ GroupPolicyProvider::class ], property = [ DRIVER_SERVICE ])
@ServiceRanking(DRIVER_SERVICE_RANKING)
class GroupPolicyProviderImpl : GroupPolicyProvider {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy? {
        return null
    }

    override fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit) {
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }
}
