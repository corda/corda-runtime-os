package net.corda.membership.impl.read

import net.corda.lifecycle.Lifecycle
import net.corda.membership.read.MembershipGroupServiceFactory
import net.corda.membership.read.MembershipGroupServiceFactoryProvider
import net.corda.membership.config.MembershipConfig
import net.corda.membership.lifecycle.MembershipLifecycleComponent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component(service = [MembershipGroupServiceFactoryProvider::class])
class MembershipGroupServiceFactoryDefaultProviderImpl : Lifecycle, MembershipGroupServiceFactoryProvider,
    MembershipLifecycleComponent {
    companion object {
        private val logger: Logger = contextLogger()
        const val PROVIDER_NAME = "default"
    }

    override val name: String get() = PROVIDER_NAME

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

    override fun create(): MembershipGroupServiceFactory {
        TODO("Not yet implemented")
    }
}
