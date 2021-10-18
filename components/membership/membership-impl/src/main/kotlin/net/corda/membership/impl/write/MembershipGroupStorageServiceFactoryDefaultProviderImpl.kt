package net.corda.membership.impl.write

import net.corda.lifecycle.Lifecycle
import net.corda.membership.write.MembershipGroupStorageServiceFactory
import net.corda.membership.write.MembershipGroupStorageServiceFactoryProvider
import net.corda.membership.config.MembershipConfig
import net.corda.membership.lifecycle.MembershipLifecycleComponent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component(service = [MembershipGroupStorageServiceFactoryProvider::class])
class MembershipGroupStorageServiceFactoryDefaultProviderImpl : Lifecycle, MembershipGroupStorageServiceFactoryProvider,
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

    override fun create(): MembershipGroupStorageServiceFactory {
        TODO("Not yet implemented")
    }
}
