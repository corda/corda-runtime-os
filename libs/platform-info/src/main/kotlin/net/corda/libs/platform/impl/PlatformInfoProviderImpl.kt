package net.corda.libs.platform.impl

import net.corda.libs.platform.PlatformInfoProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [PlatformInfoProvider::class])
class PlatformInfoProviderImpl @Activate constructor() : PlatformInfoProvider {

    internal companion object {
        const val STUB_PLATFORM_VERSION = 5000
    }

    override val platformVersion = STUB_PLATFORM_VERSION
}