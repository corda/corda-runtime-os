package net.corda.membership.impl.registration.dummy

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

interface TestGroupReaderProvider : MembershipGroupReaderProvider {
    fun loadMembers(holdingIdentity: HoldingIdentity, members: List<MemberInfo>)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipGroupReaderProvider::class, TestGroupReaderProvider::class])
class TestGroupReaderProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestGroupReaderProvider {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
    }

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun loadMembers(holdingIdentity: HoldingIdentity, members: List<MemberInfo>) {
        val reader = getGroupReader(holdingIdentity) as TestGroupReader
        reader.loadMembers(members)
    }

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader = TestGroupReader()

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

class TestGroupReader @Activate constructor() : MembershipGroupReader {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
        private val cache = mutableListOf<MemberInfo>()
    }

    override val groupId: String
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val owningMember: MemberX500Name
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val groupParameters: GroupParameters
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    fun loadMembers(members: List<MemberInfo>) = cache.addAll(members)

    override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> = cache

    override fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? =
        cache.find { it.name == name }

    override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }
    override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
}
