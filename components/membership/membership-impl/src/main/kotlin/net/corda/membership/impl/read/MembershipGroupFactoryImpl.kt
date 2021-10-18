package net.corda.membership.impl.read

import net.corda.lifecycle.Lifecycle
import net.corda.membership.read.MembershipGroupFactory
import net.corda.membership.read.MembershipGroupServiceFactory
import net.corda.membership.read.MembershipGroupServiceFactoryProvider
import net.corda.membership.config.MembershipConfig
import net.corda.membership.lifecycle.MembershipLifecycleComponent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import org.slf4j.Logger

@Component(service = [MembershipGroupFactory::class])
class MembershipGroupFactoryImpl @Activate constructor(
    @Reference(
        service = MembershipGroupServiceFactoryProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val providers: List<MembershipGroupServiceFactoryProvider>
) : Lifecycle, MembershipGroupFactory, MembershipLifecycleComponent {
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

    override fun getMembershipGroupServiceFactory(): MembershipGroupServiceFactory {
        TODO("Not yet implemented")
    }
}
