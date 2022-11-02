package net.corda.ledger.common.testkit

import net.corda.libs.platform.PlatformInfoProvider

class MockPlatformInfoProvider(
    override val activePlatformVersion: Int = 123,
    override val localWorkerPlatformVersion: Int = 456,
    override val localWorkerSoftwareVersion: String = "789"
): PlatformInfoProvider

fun mockPlatformInfoProvider(): PlatformInfoProvider {
    return MockPlatformInfoProvider()
}