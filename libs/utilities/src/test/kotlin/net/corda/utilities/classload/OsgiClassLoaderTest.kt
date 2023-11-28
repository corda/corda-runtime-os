package net.corda.utilities.classload

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.wiring.BundleRevision
import org.osgi.framework.wiring.BundleRevision.TYPE_FRAGMENT
import java.net.URL
import java.util.Collections.enumeration

class OsgiClassLoaderTest {
    private companion object {
        private const val RESOURCE_NAME = "resource-name"
        private val resource1 = URL("jar:file://location1/bundle.jar!/$RESOURCE_NAME")
        private val resource2 = URL("jar:file://location2/directory/bundle.jar!/$RESOURCE_NAME")
        private val resource3 = URL("jar:file://location3/bundle.jar!/$RESOURCE_NAME")
    }

    private val nonFragment = mock<BundleRevision>().apply {
        whenever(types).thenReturn(0)
    }
    private val fragment = mock<BundleRevision>().apply {
        whenever(types).thenReturn(TYPE_FRAGMENT)
    }

    private val noResources = mock<Bundle>().apply {
        whenever(adapt(BundleRevision::class.java)).thenReturn(nonFragment)
    }
    private val oneResource = mock<Bundle>().apply {
        whenever(getResources(RESOURCE_NAME)).thenReturn(enumeration(listOf(resource1)))
        whenever(adapt(BundleRevision::class.java)).thenReturn(nonFragment)
    }
    private val twoResources = mock<Bundle>().apply {
        whenever(getResources(RESOURCE_NAME)).thenReturn(enumeration(listOf(resource2, resource3)))
        whenever(adapt(BundleRevision::class.java)).thenReturn(nonFragment)
    }
    private val fragmentResources = mock<Bundle>().apply {
        whenever(adapt(BundleRevision::class.java)).thenReturn(fragment)
    }

    @Test
    fun testGetResourcesWithoutBundles() {
        val resources = OsgiClassLoader(emptyList())
            .getResources(RESOURCE_NAME)
        assertThat(resources.hasMoreElements()).isFalse()
        assertThat(resources.asSequence().toList()).isEmpty()
    }

    @Test
    fun testGetResourcesFromEmptyBundle() {
        val resources = OsgiClassLoader(listOf(noResources))
            .getResources(RESOURCE_NAME)
        assertThat(resources.hasMoreElements()).isFalse()
        assertThat(resources.asSequence().toList()).isEmpty()
        verify(noResources).getResources(RESOURCE_NAME)
    }

    @Test
    fun testGetResourcesFromBundleWithOneResource() {
        val resources = OsgiClassLoader(listOf(oneResource))
            .getResources(RESOURCE_NAME)
        assertThat(resources.hasMoreElements()).isTrue()
        assertThat(resources.asSequence().toList()).containsExactly(resource1)
        verify(oneResource).getResources(RESOURCE_NAME)
    }

    @Test
    fun testGetResourcesFromBundleWithTwoResources() {
        val resources = OsgiClassLoader(listOf(twoResources))
            .getResources(RESOURCE_NAME)
        assertThat(resources.hasMoreElements()).isTrue()
        assertThat(resources.asSequence().toList()).containsExactly(resource2, resource3)
        verify(twoResources).getResources(RESOURCE_NAME)
    }

    @Test
    fun testGetResourcesFromAllBundles() {
        val resources = OsgiClassLoader(listOf(noResources, oneResource, fragmentResources, twoResources))
            .getResources(RESOURCE_NAME)
        assertThat(resources.hasMoreElements()).isTrue()
        assertThat(resources.asSequence().toList()).containsExactly(resource1, resource2, resource3)
        verify(noResources).getResources(RESOURCE_NAME)
        verify(oneResource).getResources(RESOURCE_NAME)
        verify(twoResources).getResources(RESOURCE_NAME)
        verify(fragmentResources, never()).getResources(RESOURCE_NAME)
    }

    @Test
    fun testNoResourcesFromFragment() {
        val resources = OsgiClassLoader(listOf(fragmentResources)).getResources(RESOURCE_NAME)
        assertThat(resources.hasMoreElements()).isFalse()
        assertThat(resources.asSequence().toList()).isEmpty()
        verify(fragmentResources, never()).getResources(RESOURCE_NAME)
    }
}
