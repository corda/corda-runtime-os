package net.corda.ledger.lib.impl.stub.platform

import net.corda.libs.platform.PlatformInfoProvider

class StubPlatformInfoProvider : PlatformInfoProvider {
    override val activePlatformVersion: Int
        get() = 1
    override val localWorkerPlatformVersion: Int
        get() = TODO("Not yet implemented")
    override val localWorkerSoftwareVersion: String
        get() = TODO("Not yet implemented")
}