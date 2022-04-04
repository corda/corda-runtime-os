package net.corda.testing.sandboxes.impl

import net.corda.membership.CPIWhiteList
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component
@ServiceRanking(Int.MAX_VALUE)
class MembershipGroupReaderProviderImpl : MembershipGroupReaderProvider {
    private val logger = loggerFor<MembershipGroupReaderProvider>()

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader {
        return MembershipGroupReaderImpl(holdingIdentity)
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Starting")
    }

    override fun stop() {
        logger.info("Stopping")
    }

    private class MembershipGroupReaderImpl(holdingIdentity: HoldingIdentity) : MembershipGroupReader {
        override val groupId: String = holdingIdentity.groupId
        override val owningMember: MemberX500Name = MemberX500Name.parse(holdingIdentity.x500Name)

        override val groupParameters: GroupParameters
            get() = TODO("groupParameters: Not yet implemented")
        override val cpiWhiteList: CPIWhiteList
            get() = TODO("cpiWhiteList: Not yet implemented")

        override fun lookup(): Collection<MemberInfo> {
            throw IllegalStateException("TEST MODULE: Membership not supported")
        }

        override fun lookup(publicKeyHash: PublicKeyHash): MemberInfo? {
            return null
        }

        override fun lookup(name: MemberX500Name): MemberInfo? {
            return null
        }
    }
}
