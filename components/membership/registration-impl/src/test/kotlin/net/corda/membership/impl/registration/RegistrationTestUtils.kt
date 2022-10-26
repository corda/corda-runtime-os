package net.corda.membership.impl.registration

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.*

const val TEST_CPI_NAME = "cpi-name"
const val TEST_CPI_VERSION = "1.1"
const val TEST_PLATFORM_VERSION = 5000
const val TEST_SOFTWARE_VERSION = "5.0.0.0-SNAPSHOT"

fun String.addIndex(arg: Int) = String.format(this, arg)

fun buildTestVirtualNodeInfo(member: HoldingIdentity) = VirtualNodeInfo(
    holdingIdentity = member,
    cpiIdentifier = CpiIdentifier(TEST_CPI_NAME, TEST_CPI_VERSION, null),
    vaultDmlConnectionId = UUID.randomUUID(),
    cryptoDmlConnectionId = UUID.randomUUID(),
    uniquenessDmlConnectionId = UUID.randomUUID(),
    timestamp = Instant.ofEpochSecond(1)
)

fun buildMockPlatformInfoProvider(): PlatformInfoProvider = mock {
    on { activePlatformVersion } doReturn TEST_PLATFORM_VERSION
    on { localWorkerSoftwareVersion } doReturn TEST_SOFTWARE_VERSION
}