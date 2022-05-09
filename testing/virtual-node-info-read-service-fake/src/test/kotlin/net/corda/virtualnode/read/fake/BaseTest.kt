package net.corda.virtualnode.read.fake

import net.corda.libs.packaging.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.util.*

internal abstract class BaseTest {
    protected val alice = VirtualNodeInfo(
        HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "flow-worker-dev"),
        CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null),
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )

    protected val bob = VirtualNodeInfo(
        HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "flow-worker-dev"),
        CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null),
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )

    protected val carol = VirtualNodeInfo(
        HoldingIdentity("CN=Carol, O=Carol Corp, L=LDN, C=GB", "flow-worker-dev"),
        CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null),
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )
}