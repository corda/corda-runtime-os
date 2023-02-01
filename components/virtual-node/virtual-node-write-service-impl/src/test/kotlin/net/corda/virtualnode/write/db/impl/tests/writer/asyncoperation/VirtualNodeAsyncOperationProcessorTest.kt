package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.IllegalArgumentException


class VirtualNodeAsyncOperationProcessorTest {

    private val virtualNodeUpgradeHandler = mock<VirtualNodeAsyncOperationHandler<VirtualNodeUpgradeRequest>>()
    private val processor = VirtualNodeAsyncOperationProcessor(virtualNodeUpgradeHandler)

    @Test
    fun `onNext handles exceptions without taking down the worker`() {
        whenever(virtualNodeUpgradeHandler.handle(any(), any(), any())).thenThrow(IllegalArgumentException("some error"))


        val result = processor.onNext(
            listOf(
                Record("topic", "key", mock<VirtualNodeAsynchronousRequest>())
            )
        )

        assertThat(result).isNotNull
        assertThat(result).isEmpty()
    }
}