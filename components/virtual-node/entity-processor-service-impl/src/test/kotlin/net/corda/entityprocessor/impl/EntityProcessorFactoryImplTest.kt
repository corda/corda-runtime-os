package net.corda.entityprocessor.impl

import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

internal class EntityProcessorFactoryImplTest {
    @Test
    fun `payload check throws if max bytes exceeded`() {
        val bytes = ByteBuffer.wrap(ByteArray(10 * 1024 * 1024))
        assertThrows<KafkaMessageSizeException> { EntityProcessorFactoryImpl.payloadSizeCheck(bytes) }
    }
}
