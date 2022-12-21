package net.corda.utilities.classload

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.net.URL
import java.util.*

class OsgiDelegatedClassLoaderTest {
    private val bundle = mock<Bundle>()
    private val cl = OsgiDelegatedClassLoader(bundle)

    @Test
    fun `load class returns class from bundle`() {
        whenever(bundle.loadClass("example")).thenReturn(OsgiDelegatedClassLoaderTest::class.java)
        assertThat(cl.loadClass("example")).isEqualTo(OsgiDelegatedClassLoaderTest::class.java)
    }

    @Test
    fun `get resources returns resources from bundle`() {
        val url = URL("http://")
        val urls = Vector<URL>().apply { add(url) }
        whenever(bundle.getResources("example")).thenReturn(urls.elements())
        assertThat(cl.getResources("example").toList()).containsOnly(url)
    }

    @Test
    fun `get resource returns resource from bundle`() {
        val url = URL("http://")
        whenever(bundle.getResource("example")).thenReturn(url)
        assertThat(cl.getResource("example")).isEqualTo(url)
    }
}
