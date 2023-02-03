package net.corda.membership.impl.synchronisation.dummy

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.time.Instant

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
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = GroupParametersFactory::class)
    private val groupParametersFactory: GroupParametersFactory
) : TestGroupReaderProvider {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private val groupReader = TestGroupReader(groupParametersFactory)

    override fun loadMembers(holdingIdentity: HoldingIdentity, memberList: List<MemberInfo>) {
        val reader = getGroupReader(holdingIdentity) as TestGroupReader
        reader.loadMembers(memberList)
    }

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader = groupReader

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("${this::class.java.simpleName} starting.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("${this::class.java.simpleName} stopping.")
        coordinator.stop()
    }
}

class TestGroupReader(private val groupParametersFactory: GroupParametersFactory) : MembershipGroupReader {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
        private const val EPOCH = "5"
        private const val PLATFORM_VERSION = "5000"
    }

    override val groupId: String
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val owningMember: MemberX500Name
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
    override val groupParameters: GroupParameters
        get() = groupParametersFactory.create(
            KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, EPOCH),
                    KeyValuePair(MPV_KEY, PLATFORM_VERSION),
                    KeyValuePair(MODIFIED_TIME_KEY, Instant.now().toString()),
                )
            )
        )
    private var members = emptyList<MemberInfo>()

    fun loadMembers(memberList: List<MemberInfo>) {
        members = memberList
    }

    override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> = members

    override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? = members.firstOrNull { it.holdingIdentity.x500Name == name }

    override fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

   override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
        get() = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)
}