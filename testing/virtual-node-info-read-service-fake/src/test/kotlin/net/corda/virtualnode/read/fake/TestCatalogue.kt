package net.corda.virtualnode.read.fake

import net.corda.libs.packaging.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.util.*

object TestCatalogue {

    object Identity {
        fun alice(groupId: String): HoldingIdentity {
            return HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", groupId)
        }

        fun bob(groupId: String): HoldingIdentity {
            return HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
        }

        fun carol(groupId: String): HoldingIdentity {
            return HoldingIdentity("CN=Carol, O=Carol Corp, L=LDN, C=GB", groupId)
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
                vaultDmlConnectionId = UUID.randomUUID()
            )
        }
    }
}