package net.corda.interop.service.integration

import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipGroupReaderProvider::class, MembershipGroupReaderProviderFake::class])
class MembershipGroupReaderProviderFake : MembershipGroupReaderProvider {
    private val groupReaders = mutableMapOf<HoldingIdentity, MembershipGroupReader>()

    fun put(holdingIdentity: HoldingIdentity) {
        groupReaders[holdingIdentity] = MembershipGroupReaderFake()
    }

    fun reset(){
        groupReaders.clear()
    }

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader {
        return checkNotNull(groupReaders[holdingIdentity])
        {
            "Failed to find membership group reader for '${holdingIdentity.x500Name}'"
        }
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    class MembershipGroupReaderFake :MembershipGroupReader{
        override val groupId: String
            get() = ""
        override val owningMember: MemberX500Name
            get() = TODO("Not yet implemented")
        override val groupParameters: GroupParameters
            get() = TODO("Not yet implemented")

        override fun lookup(): Collection<MemberInfo> {
            TODO("Not yet implemented")
        }

        override fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash): MemberInfo? {
            TODO("Not yet implemented")
        }

        override fun lookup(name: MemberX500Name): MemberInfo? {
            TODO("Not yet implemented")
        }

        override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash): MemberInfo? {
            TODO("Not yet implemented")
        }

        override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
            get() = TODO("Not yet implemented")
    }
}