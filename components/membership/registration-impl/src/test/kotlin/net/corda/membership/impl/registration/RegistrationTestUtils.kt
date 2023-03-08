package net.corda.membership.impl.registration

import net.corda.crypto.core.parseSecureHash
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.crypto.SecureHash
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

val testCpiSignerSummaryHash = parseSecureHash("ALG:A1B2C3D4")

fun buildTestVirtualNodeInfo(member: HoldingIdentity) = VirtualNodeInfo(
    holdingIdentity = member,
    cpiIdentifier = CpiIdentifier(TEST_CPI_NAME, TEST_CPI_VERSION, testCpiSignerSummaryHash),
    vaultDmlConnectionId = UUID(0, 1),
    cryptoDmlConnectionId = UUID(0, 1),
    uniquenessDmlConnectionId = UUID(0, 1),
    timestamp = Instant.ofEpochSecond(1)
)

fun buildMockPlatformInfoProvider(): PlatformInfoProvider = mock {
    on { activePlatformVersion } doReturn TEST_PLATFORM_VERSION
    on { localWorkerSoftwareVersion } doReturn TEST_SOFTWARE_VERSION
}