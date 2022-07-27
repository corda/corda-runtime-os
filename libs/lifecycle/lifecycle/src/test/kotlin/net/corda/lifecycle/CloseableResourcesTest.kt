package net.corda.lifecycle

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class CloseableResourcesTest {

    private var closeable1: AutoCloseable? = null
    private var closeable2: AutoCloseable? = null

    @Test
    fun `resources are closed correctly`() {
        val resources = CloseableResources.of(
            ::closeable1,
            ::closeable2
        )

        closeable1 = mock()
        closeable2 = mock()

        resources.closeResources()

        verify(closeable1)?.close()
        verify(closeable2)?.close()
    }
}
