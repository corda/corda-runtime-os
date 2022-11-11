package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.gateway.certificates.Active
import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.data.p2p.gateway.certificates.RevocationMode
import net.corda.data.p2p.gateway.certificates.Revoked
import net.corda.lifecycle.domino.logic.util.RPCSenderWithDominoLogic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

class RevocationCheckerClientTest {

    private val mockRPCSenderConstruction = Mockito.mockConstruction(RPCSenderWithDominoLogic::class.java)
    private val client = RevocationCheckerClient(mock(), mock(), mock())
    @Suppress("UNCHECKED_CAST")
    private val mockRPCSender = mockRPCSenderConstruction.constructed().first()
            as RPCSenderWithDominoLogic<RevocationCheckRequest, RevocationCheckResponse>
    private val mockRequest = mock<RevocationCheckRequest>()
    @AfterEach
    fun cleanUp() {
        mockRPCSenderConstruction.close()
    }

    @Test
    fun `RevocationCheckerClient delegates to the rpc sender`() {
        whenever(mockRPCSender.sendRequest(any())).thenReturn(
            CompletableFuture.completedFuture(RevocationCheckResponse(Active())
        ))
        client.checkRevocation(mockRequest)
        verify(mockRPCSender).sendRequest(any())
    }

    @Test
    fun `if request times out the certificate is treated as active if SOFT_FAIL mode is used`() {
        val future = mock<CompletableFuture<RevocationCheckResponse>> {
            on {get(any(), any())}.thenThrow(TimeoutException("Future timed out!"))
        }
        whenever(mockRequest.mode).thenReturn(RevocationMode.SOFT_FAIL)
        whenever(mockRPCSender.sendRequest(mockRequest)).thenReturn(future)
        assertThat(client.checkRevocation(mockRequest).status).isEqualTo(Active())
    }

    @Test
    fun `if request times out the certificate is treated as revoked if HARD_FAIL mode is used`() {
        val future = mock<CompletableFuture<RevocationCheckResponse>> {
            on {get(any(), any())}.thenThrow(TimeoutException("Future timed out!"))
        }
        whenever(mockRequest.mode).thenReturn(RevocationMode.HARD_FAIL)
        whenever(mockRPCSender.sendRequest(mockRequest)).thenReturn(future)
        assertThat(client.checkRevocation(mockRequest).status).isInstanceOf(Revoked::class.java)
    }

}