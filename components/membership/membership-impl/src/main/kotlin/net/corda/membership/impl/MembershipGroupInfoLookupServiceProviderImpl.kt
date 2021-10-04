package net.corda.membership.impl

import net.corda.lifecycle.Lifecycle
import net.corda.membership.MembershipGroupInfoLookupService
import net.corda.membership.MembershipGroupInfoLookupServiceProvider
import net.corda.membership.config.MembershipConfig
import net.corda.membership.lifecycle.MembershipLifecycleComponent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component(service = [MembershipGroupInfoLookupServiceProvider::class])
class MembershipGroupInfoLookupServiceProviderImpl(
    override val name: String
) : Lifecycle, MembershipGroupInfoLookupServiceProvider, MembershipLifecycleComponent {
    companion object {
        private val logger: Logger = contextLogger()
    }

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        isRunning = false
    }

    override fun handleConfigEvent(config: MembershipConfig) {
        TODO("Not yet implemented")
    }

    override fun create(): MembershipGroupInfoLookupService {
        TODO("Not yet implemented")
    }
}
