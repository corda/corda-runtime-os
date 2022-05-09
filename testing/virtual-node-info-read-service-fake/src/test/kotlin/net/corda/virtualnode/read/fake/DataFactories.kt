package net.corda.virtualnode.read.fake

import net.corda.libs.packaging.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.util.*

// This extension properties and functions can be moved to a shared module to be reused across multiple tests.

val HoldingIdentity.Companion.alice: HoldingIdentity
    get() = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "flow-worker-dev")

val HoldingIdentity.Companion.bob: HoldingIdentity
    get() = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "flow-worker-dev")

val HoldingIdentity.Companion.carol: HoldingIdentity
    get() = HoldingIdentity("CN=Carol, O=Carol Corp, L=LDN, C=GB", "flow-worker-dev")

val CpiIdentifier.Companion.flowWorkerDev: CpiIdentifier
    get() = CpiIdentifier("flow-worker-dev", "5.0.0.0-SNAPSHOT", null)

val VirtualNodeInfo.Companion.alice: VirtualNodeInfo
    get() = VirtualNodeInfo(
        HoldingIdentity.alice,
        CpiIdentifier.flowWorkerDev,
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )

val VirtualNodeInfo.Companion.bob: VirtualNodeInfo
    get() = VirtualNodeInfo(
        HoldingIdentity.bob,
        CpiIdentifier.flowWorkerDev,
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )

val VirtualNodeInfo.Companion.carol: VirtualNodeInfo
    get() = VirtualNodeInfo(
        HoldingIdentity.carol,
        CpiIdentifier.flowWorkerDev,
        cryptoDmlConnectionId = UUID.randomUUID(),
        vaultDmlConnectionId = UUID.randomUUID()
    )
