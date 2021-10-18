package net.corda.membership.impl

import net.corda.lifecycle.Lifecycle
import net.corda.membership.MembershipGroupStorageFactory
import net.corda.membership.MembershipGroupStorageServiceFactory
import net.corda.membership.MembershipGroupStorageServiceFactoryProvider
import net.corda.membership.config.MembershipConfig
import net.corda.membership.lifecycle.MembershipLifecycleComponent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [MembershipGroupStorageFactory::class])
class MembershipGroupStorageFactoryImpl @Activate constructor(
    @Reference(service = MembershipGroupStorageServiceFactoryProvider::class)
    private val providers: List<MembershipGroupStorageServiceFactoryProvider>
) : Lifecycle, MembershipGroupStorageFactory, MembershipLifecycleComponent {
    companion object {
        private val logger: Logger = contextLogger()
    }

    override val isRunning: Boolean
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun handleConfigEvent(config: MembershipConfig) {
        TODO("Not yet implemented")
    }

    override fun getMembershipGroupStorageServiceFactory(): MembershipGroupStorageServiceFactory {
        TODO("Not yet implemented")
    }
}
