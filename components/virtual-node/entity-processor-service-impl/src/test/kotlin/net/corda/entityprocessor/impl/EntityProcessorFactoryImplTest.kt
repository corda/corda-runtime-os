package net.corda.entityprocessor.impl

import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

internal class EntityProcessorFactoryImplTest {
    @Test
    fun `payload check throws if max bytes exceeded`() {
        val maxSize = 1024 * 10
        val bytes = ByteBuffer.wrap(ByteArray(maxSize + 1))
        assertThrows<KafkaMessageSizeException> { EntityProcessorFactoryImpl.PayloadChecker(maxSize).checkSize(bytes) }
    }
}
