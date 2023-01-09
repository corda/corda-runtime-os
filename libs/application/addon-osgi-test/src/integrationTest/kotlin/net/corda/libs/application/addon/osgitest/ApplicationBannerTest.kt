package net.corda.libs.application.addon.osgitest

import net.corda.application.addon.CordaAddon
import net.corda.application.banner.StartupBanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ServiceRanking(0)
@Component(service = [StartupBanner::class])
class TestBanner : StartupBanner {
    override fun get(name: String, version: String): String {
        return "Integration Test $name $version"
    }
}

@Component(service = [CordaAddon::class])
class TestAddon1 : CordaAddon {
    override val name: String
        get() = "Addon 1"
    override val licence: String
        get() = "Licence 1"
    override val vendor: String
        get() = "Vendor 1"
    override val description: String
        get() = "Addon description 1"
}

@Component(service = [CordaAddon::class])
class TestAddon2 : CordaAddon {
    override val name: String
        get() = "Addon 2"
    override val licence: String
        get() = "Licence 2"
    override val vendor: String
        get() = "Vendor 2"
    override val description: String
        get() = "Addon description 2"
}

@ExtendWith(ServiceExtension::class)
class ApplicationBannerTest {
    companion object {
        private const val INJECT_TIMEOUT = 10000L
    }

    @InjectService(timeout = INJECT_TIMEOUT)
    lateinit var startupBanner: StartupBanner

    @InjectService(timeout = INJECT_TIMEOUT, cardinality = 2)
    lateinit var addons: List<CordaAddon>

    @Test
    fun `Can discover new banner`() {
        assertThat(startupBanner).isInstanceOf(TestBanner::class.java)
    }

    @Test
    fun `Can discover multiple add-ons`() {
        assertThat(addons).filteredOn { it is TestAddon1 }.isNotEmpty
        assertThat(addons).filteredOn { it is TestAddon2 }.isNotEmpty
    }
}