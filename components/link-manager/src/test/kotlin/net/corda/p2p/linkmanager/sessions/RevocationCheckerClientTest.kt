package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckStatus
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
            as RPCSenderWithDominoLogic<RevocationCheckRequest, RevocationCheckStatus>

    @AfterEach
    fun cleanUp() {
        mockRPCSender.close()
    }

    @Test
    fun `RevocationCheckerClient delegates to the rpc sender`() {
        whenever(mockRPCSender.sendRequest(any())).thenReturn(CompletableFuture.completedFuture(RevocationCheckStatus.ACTIVE))
        client.checkRevocation(mock())
        verify(mockRPCSender).sendRequest(any())
    }

    @Test
    fun `if request times out the certificate is treated as revoked`() {
        val future = mock<CompletableFuture<RevocationCheckStatus>> {
            on {get(any(), any())}.thenThrow(TimeoutException("Future timed out!"))
        }
        whenever(mockRPCSender.sendRequest(any())).thenReturn(future)
        assertThat(client.checkRevocation(mock())).isEqualTo(RevocationCheckStatus.REVOKED)
    }

}