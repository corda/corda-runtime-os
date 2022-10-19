package net.corda.ledger.consensual.persistence.impl.processor.factory

import net.corda.persistence.common.PayloadChecker
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

internal class ConsensualLedgerProcessorFactoryImplTest {
    @Test
    fun `payload check throws if max bytes exceeded`() {
        val maxSize = 1024 * 10
        val bytes = ByteBuffer.wrap(ByteArray(maxSize + 1))
        assertThrows<KafkaMessageSizeException> { PayloadChecker(maxSize).checkSize(bytes) }
    }
}
