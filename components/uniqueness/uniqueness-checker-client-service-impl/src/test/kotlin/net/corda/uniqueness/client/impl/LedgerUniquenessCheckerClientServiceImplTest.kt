package net.corda.uniqueness.client.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerUniquenessCheckerClientServiceImplTest {

    private companion object {
        val dummyTxId = SecureHashUtils.randomSecureHash()
    }

    private val argumentCaptor = argumentCaptor<Class<out UniquenessCheckExternalEventFactory>>()

    @Test
    fun `Signing is successful when uniqueness check was successful`() {
        val response = createClientService(
            uniquenessCheckResult = UniquenessCheckResultSuccessImpl(Instant.now())
        ).requestUniquenessCheck(
            dummyTxId.toString(),
            emptyList(),
            emptyList(),
            5,
            null,
            Instant.now()
        )

        assertThat(response).isInstanceOf(UniquenessCheckResultSuccess::class.java)
    }

    @Test
    fun `Uniqueness check client responds with failure if uniqueness check has failed`() {
        val response = createClientService(
            uniquenessCheckResult = UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorMalformedRequestImpl("Malformed")
            )
        ).requestUniquenessCheck(
            dummyTxId.toString(),
            emptyList(),
            emptyList(),
            5,
            null,
            Instant.now(),
        )

        assertThat(response).isInstanceOf(UniquenessCheckResultFailure::class.java)
        assertThat((response as UniquenessCheckResultFailure).error).isInstanceOf(
            UniquenessCheckErrorMalformedRequest::class.java
        )
    }

    private fun createClientService(
        uniquenessCheckResult: UniquenessCheckResult? = UniquenessCheckResultSuccessImpl(Instant.now())
    ): LedgerUniquenessCheckerClientService {
        val mockExternalEventExecutor = mock<ExternalEventExecutor>()
        whenever(mockExternalEventExecutor.execute(argumentCaptor.capture(), any()))
            .thenReturn(uniquenessCheckResult)

        return LedgerUniquenessCheckerClientServiceImpl(
            mockExternalEventExecutor
        )
    }
}