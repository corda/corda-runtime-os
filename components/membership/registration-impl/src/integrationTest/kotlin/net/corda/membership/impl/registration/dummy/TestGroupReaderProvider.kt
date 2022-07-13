package net.corda.membership.impl.registration.dummy

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.CPIWhiteList
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

interface TestGroupReaderProvider : MembershipGroupReaderProvider

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipGroupReaderProvider::class, TestGroupReaderProvider::class])
class TestGroupReaderProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MemberInfoFactory::class)
    val memberInfoFactory: MemberInfoFactory,
) : TestGroupReaderProvider {
    companion object {
        val logger = contextLogger()
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
    }

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader =
        TestGroupReader(memberInfoFactory)

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("TestGroupReaderProvider starting.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("TestGroupReaderProvider starting.")
        coordinator.stop()
    }
}

class TestGroupReader @Activate constructor(
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
) : MembershipGroupReader {
    companion object {
        val logger = contextLogger()
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
    }

    override val groupId: String
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val owningMember: MemberX500Name
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val groupParameters: GroupParameters
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val cpiWhiteList: CPIWhiteList
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    private val name = MemberX500Name("Corda MGM", "London", "GB")
    private val group = "dummy_group"

    override fun lookup(): Collection<MemberInfo> = listOf(
        memberInfoFactory.create(
            sortedMapOf(
                PARTY_NAME to name.toString(),
                GROUP_ID to group,
                "corda.endpoints.0.connectionURL" to "localhost:1081",
                "corda.endpoints.0.protocolVersion" to "1",
                PLATFORM_VERSION to "5000",
                SOFTWARE_VERSION to "5.0.0",
            ),
            sortedMapOf(
                IS_MGM to "true",
            )
        )
    )

    override fun lookup(ledgerKeyHash: PublicKeyHash): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookup(name: MemberX500Name): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }
}
