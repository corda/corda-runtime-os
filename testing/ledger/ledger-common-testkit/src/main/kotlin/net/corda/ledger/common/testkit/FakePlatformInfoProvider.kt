package net.corda.ledger.common.testkit

import net.corda.libs.platform.PlatformInfoProvider

class FakePlatformInfoProvider(
    override val activePlatformVersion: Int = 50200,
    override val localWorkerPlatformVersion: Int = 456,
    override val localWorkerSoftwareVersion: String = "789"
): PlatformInfoProvider

fun fakePlatformInfoProvider(): PlatformInfoProvider {
    return FakePlatformInfoProvider()
}