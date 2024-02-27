package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.OffsetResultSetExecutor
import net.corda.flow.persistence.query.StableResultSetExecutor
import net.corda.internal.serialization.SerializedBytesImpl
import net.corda.v5.application.serialization.SerializationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ResultSetFactoryImplTest {

    private val serializationService = mock<SerializationService>()
    private val resultSetFactory = ResultSetFactoryImpl(serializationService)

    @Test
    fun `serializes the parameters and creates an offset result set`() {
        whenever(serializationService.serialize(any<Any>())).thenReturn(SerializedBytesImpl(byteArrayOf(1, 2, 3, 4)))
        val parameters = mapOf("A" to 1, "B" to 2, "C" to 3)
        resultSetFactory.create(parameters, 5, 0, Any::class.java) { _, _ -> OffsetResultSetExecutor.Results(emptyList(), 0) }
        verify(serializationService).serialize(1)
        verify(serializationService).serialize(2)
        verify(serializationService).serialize(3)
    }

    @Test
    fun `serializes the parameters and creates a stable result set`() {
        whenever(serializationService.serialize(any<Any>())).thenReturn(SerializedBytesImpl(byteArrayOf(1, 2, 3, 4)))
        val parameters = mapOf("A" to 1, "B" to 2, "C" to 3)
        resultSetFactory.create(parameters, 5,  Any::class.java) { _, _, _ -> StableResultSetExecutor.Results(emptyList(), null, null) }
        verify(serializationService).serialize(1)
        verify(serializationService).serialize(2)
        verify(serializationService).serialize(3)
    }
}
