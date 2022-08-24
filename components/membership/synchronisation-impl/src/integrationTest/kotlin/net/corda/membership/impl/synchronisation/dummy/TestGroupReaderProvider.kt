package net.corda.membership.impl.synchronisation.dummy

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.CPIWhiteList
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

/**
 * Created for mocking and simplifying group reader functionalities used by the membership services.
 */
interface TestGroupReaderProvider : MembershipGroupReaderProvider {
    fun loadMembers(holdingIdentity: HoldingIdentity, memberList: List<MemberInfo>)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipGroupReaderProvider::class, TestGroupReaderProvider::class])
class TestGroupReaderProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory
) : TestGroupReaderProvider {
    companion object {
        val logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private val groupReader = TestGroupReader()

    override fun loadMembers(holdingIdentity: HoldingIdentity, memberList: List<MemberInfo>) {
        val reader = getGroupReader(holdingIdentity) as TestGroupReader
        reader.loadMembers(memberList)
    }

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader = groupReader

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

class TestGroupReader : MembershipGroupReader {
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

    private var members = emptyList<MemberInfo>()

    fun loadMembers(memberList: List<MemberInfo>) {
        members = memberList
    }

    override fun lookup(): Collection<MemberInfo> = members

    override fun lookup(name: MemberX500Name): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash): MemberInfo? {
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