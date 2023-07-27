package net.corda.testing.sandboxes.impl

import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(property = [ SandboxSetup.SANDBOX_SERVICE ])
@ServiceRanking(SandboxSetup.SANDBOX_SERVICE_RANKING)
class MembershipGroupReaderProviderImpl : MembershipGroupReaderProvider {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader {
        return MembershipGroupReaderImpl(holdingIdentity)
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }

    private class MembershipGroupReaderImpl(holdingIdentity: HoldingIdentity) : MembershipGroupReader {
        override val groupId: String = holdingIdentity.groupId
        override val owningMember: MemberX500Name = holdingIdentity.x500Name

        override val groupParameters: InternalGroupParameters
            get() = TODO("groupParameters: Not yet implemented")

        override val signedGroupParameters: SignedGroupParameters
            get() = TODO("groupParameters: Not yet implemented")

        override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> {
            throw IllegalStateException("TEST MODULE: Membership not supported")
        }

        override fun lookupByLedgerKey(ledgerKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? {
            return null
        }

        override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? {
            return null
        }

        override fun lookupBySessionKey(sessionKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? {
            return null
        }

        override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
            get() = throw IllegalStateException("TEST MODULE: Membership not supported")
    }
}
