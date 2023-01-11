package net.corda.libs.platform.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.BundleContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.Collections
import java.util.jar.Attributes
import java.util.jar.Manifest

class PlatformInfoProviderImplTest {

    companion object {
        const val PLATFORM_VERSION = "999"
    }

    private val bundleContext = mock<BundleContext>().also {
        whenever(it.getProperty(eq("net.corda.platform.version"))).thenReturn(PLATFORM_VERSION)
    }
    private val platformVersionService = PlatformInfoProviderImpl(bundleContext)

    /**
     * Temporary test until real implementation is added.
     * Stub value and this can be removed once real implementation is available.
     */
    @Test
    fun `active platform version returns stub value`() {
        assertThat(
            platformVersionService.activePlatformVersion
        ).isEqualTo(
            PlatformInfoProviderImpl.STUB_PLATFORM_VERSION
        )
    }

    @Test
    fun `local worker platform version returns stub value`() {
        assertThat(platformVersionService.localWorkerPlatformVersion).isEqualTo(PLATFORM_VERSION.toInt())
    }

    @Test
    fun `local worker software version returns software version from bundle manifest`() {
        val urlOne = urlFromManifest(emptyMap())
        val urlTwo = urlFromManifest(mapOf("Bundle-SymbolicName" to "non"))
        val urlThree = urlFromManifest(mapOf("Bundle-Version" to "non"))
        val urlFour = urlFromManifest(mapOf("Bundle-Version" to "version", "Bundle-SymbolicName" to "net.corda.platform-info"))
        val classLoader = mock<ClassLoader> {
            on { getResources("META-INF/MANIFEST.MF") } doReturn
                Collections.enumeration(
                    listOf(
                        urlOne,
                        urlTwo,
                        urlThree,
                        urlFour,
                    )
                )
        }
        val platformVersionService = PlatformInfoProviderImpl(classLoader, bundleContext)

        assertThat(
            platformVersionService.localWorkerSoftwareVersion
        ).isEqualTo(
            "version"
        )
    }

    private fun urlFromManifest(manifest: Map<String, String>): URL {
        val jarManifest = Manifest()
        jarManifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.entries.forEach { (key, value) ->
            jarManifest.mainAttributes.putValue(key, value)
        }
        val output = ByteArrayOutputStream()
        jarManifest.write(output)
        val input = ByteArrayInputStream(output.toByteArray())
        return mock {
            on { openStream() } doReturn input
        }
    }
}
