package net.corda.applications.workers.workercommon.internal

import net.corda.application.addon.CordaAddon
import net.corda.application.addon.CordaAddonResolver
import net.corda.application.banner.ConsolePrinter
import net.corda.application.banner.StartupBanner
import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.libs.platform.PlatformInfoProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ApplicationBannerTest {
    private val mockBanner = mock<StartupBanner>() {
        on { get(any(), any()) } doReturn "mock banner"
    }
    private val addOn1 = mock<CordaAddon> {
        on { name } doReturn "addon 1"
        on { licence } doReturn "addon licence 1"
        on { vendor } doReturn "addon vendor 1"
        on { description } doReturn "addon description 1"
    }
    private val addOn2 = mock<CordaAddon> {
        on { name } doReturn "addon 2"
        on { licence } doReturn "addon licence 2"
        on { vendor } doReturn "addon vendor 2"
        on { description } doReturn "addon description 2"
    }
    private val mockPrinter = object {
        val printed = mutableListOf<String>()
        fun print(text: String) {
            printed.add(text)
        }
    }
    private val platformInfoProvider = mock<PlatformInfoProvider> {
        on { localWorkerSoftwareVersion } doReturn "1.2.3.4"
    }

    private val resolver = mock<CordaAddonResolver> { on { findAll() } doReturn listOf(addOn1, addOn2) }

    @Test
    fun `when show print banner`() {
        val banner = ApplicationBanner(
            mockBanner,
            resolver,
            ConsolePrinter(mockPrinter::print))
        banner.show("test", platformInfoProvider)

        verify(mockBanner).get("test", "1.2.3.4")
        assertThat(mockPrinter.printed).contains("mock banner")
    }

    @Test
    fun `when addons print details`() {
        val banner = ApplicationBanner(mockBanner, resolver, ConsolePrinter(mockPrinter::print))
        banner.show("test", platformInfoProvider)

        assertSoftly {
            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon 1") }
            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon licence 1") }
            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon vendor 1") }
            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon description 1") }

            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon 2") }
            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon licence 2") }
            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon vendor 2") }
            it.assertThat(mockPrinter.printed).anyMatch { s-> s.contains("addon description 2") }
        }
    }
}