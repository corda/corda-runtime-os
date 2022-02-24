package net.corda.chunking

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ChunkWriterFactoryTest {
    @Test
    fun `create chunk writer from factory`() {
        ChunkWriterFactory.create(1 * 1024 * 1024)
    }

    @Test
    fun `create chunk writer from factory with large chunk size throws`() {
        assertThrows<CordaRuntimeException> {
            ChunkWriterFactory.create(8 * 1024 * 1024 + 1)
        }
    }
}
