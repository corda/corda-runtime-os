package org.example.interop

import net.corda.v5.application.flows.RestRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FacadeInvocationFlowTest {
    /**
     * This is a temporary test which checks that the responding flow
     */
    @Test
    fun `facade call echos facade invocation payload back to calling flow`() {
        val flow = FacadeInvocationFlow()

        val requestBody = "Hello world!"
        val request = mock<RestRequestBody>()

        whenever(request.getRequestBody()).thenReturn(requestBody)

        val response = flow.call(request)

        assertThat(response).isEqualTo(requestBody)
    }
}
