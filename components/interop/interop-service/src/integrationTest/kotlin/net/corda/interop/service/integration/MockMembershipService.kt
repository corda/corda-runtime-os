package net.corda.interop.service.integration

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
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.security.PublicKey

interface TestGroupReaderProvider : MembershipGroupReaderProvider {
    override fun getGroupReader(holdingIdentity: net.corda.virtualnode.HoldingIdentity): MembershipGroupReader
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipGroupReaderProvider::class, TestGroupReaderProvider::class])
class MockMembershipService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestGroupReaderProvider {

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun getGroupReader(holdingIdentity: net.corda.virtualnode.HoldingIdentity): MembershipGroupReader {
        return object : MembershipGroupReader {
            override val groupId: String
                get() = TODO("Not yet implemented")
            override val owningMember: MemberX500Name
                get() = TODO("Not yet implemented")
            override val groupParameters: GroupParameters?
                get() = TODO("Not yet implemented")

            override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> {
                TODO("Not yet implemented")
            }

            override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? {
                return object : MemberInfo {
                    override fun getMemberProvidedContext(): MemberContext {
                        return object : MemberContext {
                            override fun getEntries(): MutableSet<MutableMap.MutableEntry<String, String>> {
                                TODO("Not yet implemented")
                            }

                            override fun get(key: String): String? =
                                when(key) {
                                    "corda.interop.mapping.x500name" -> "O=Alice Corp, L=LDN, C=GB"
                                    "corda.interop.mapping.group" -> "real group ID"
                                    else -> null
                                }

                            override fun <T : Any?> parse(key: String, clazz: Class<out T>): T & Any {
                                TODO("Not yet implemented")
                            }

                            override fun <T : Any?> parseOrNull(key: String, clazz: Class<out T>): T? {
                                TODO("Not yet implemented")
                            }

                            override fun <T : Any?> parseList(
                                itemKeyPrefix: String,
                                clazz: Class<out T>
                            ): MutableList<T> {
                                TODO("Not yet implemented")
                            }

                            override fun <T : Any?> parseSet(
                                itemKeyPrefix: String,
                                clazz: Class<out T>
                            ): MutableSet<T> {
                                TODO("Not yet implemented")
                            }

                        }
                    }

                    override fun getMgmProvidedContext(): MGMContext {
                        TODO("Not yet implemented")
                    }

                    override fun getName(): MemberX500Name {
                        TODO("Not yet implemented")
                    }

                    override fun getSessionInitiationKeys(): MutableList<PublicKey> {
                        TODO("Not yet implemented")
                    }

                    override fun getLedgerKeys(): MutableList<PublicKey> {
                        TODO("Not yet implemented")
                    }

                    override fun getPlatformVersion(): Int {
                        TODO("Not yet implemented")
                    }

                    override fun getSerial(): Long {
                        TODO("Not yet implemented")
                    }

                    override fun isActive(): Boolean {
                        TODO("Not yet implemented")
                    }

                }
            }

            override fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? {
                TODO("Not yet implemented")
            }

            override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? {
                TODO("Not yet implemented")
            }

            override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
                get() = TODO("Not yet implemented")

        }
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

}