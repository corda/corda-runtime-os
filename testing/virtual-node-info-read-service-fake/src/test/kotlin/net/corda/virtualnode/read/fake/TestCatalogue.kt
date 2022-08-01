package net.corda.virtualnode.read.fake

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.time.Instant
import java.util.*

object TestCatalogue {

    object Identity {
        fun alice(groupId: String): HoldingIdentity {
            return HoldingIdentity(MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"), groupId)
        }

        fun bob(groupId: String): HoldingIdentity {
            return HoldingIdentity(MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"), groupId)
        }

        fun carol(groupId: String): HoldingIdentity {
            return HoldingIdentity(MemberX500Name.parse("CN=Carol, O=Carol Corp, L=LDN, C=GB"), groupId)
        }
    }

    object CpiId {
        fun version5Snapshot(id: String): CpiIdentifier {
            return CpiIdentifier(id, "5.0.0.0-SNAPSHOT", null)
        }
    }

    object VirtualNode {
        fun create(identity: HoldingIdentity, cpiId: CpiIdentifier): VirtualNodeInfo{
            return VirtualNodeInfo(
                identity,
                cpiId,
                cryptoDmlConnectionId = UUID.randomUUID(),
                vaultDmlConnectionId = UUID.randomUUID(),
                timestamp = Instant.now()
            )
        }
    }
}
