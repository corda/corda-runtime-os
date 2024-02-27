package net.corda.membership.impl.registration.dummy

import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.registration.dummy.TestPlatformInfoProvider.Companion.TEST_ACTIVE_PLATFORM_VERSION
import net.corda.membership.impl.registration.dummy.TestPlatformInfoProvider.Companion.TEST_LOCAL_WORKER_PLATFORM_VERSION
import net.corda.membership.impl.registration.dummy.TestPlatformInfoProvider.Companion.TEST_SOFTWARE_VERSION
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

interface TestPlatformInfoProvider : PlatformInfoProvider {
    companion object {
        const val TEST_ACTIVE_PLATFORM_VERSION = 5000
        const val TEST_LOCAL_WORKER_PLATFORM_VERSION = 5001
        const val TEST_SOFTWARE_VERSION = "5.0.0.0-SNAPSNOT-test"
    }
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [PlatformInfoProvider::class, TestPlatformInfoProvider::class])
internal class TestPlatformInfoProviderImpl @Activate constructor() : TestPlatformInfoProvider {
    override val activePlatformVersion = TEST_ACTIVE_PLATFORM_VERSION
    override val localWorkerPlatformVersion = TEST_LOCAL_WORKER_PLATFORM_VERSION
    override val localWorkerSoftwareVersion = TEST_SOFTWARE_VERSION
}
