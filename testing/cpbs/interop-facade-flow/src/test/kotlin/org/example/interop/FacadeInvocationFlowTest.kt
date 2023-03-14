package org.example.interop

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.messaging.FlowMessaging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FacadeInvocationFlowTest {
    @Test
    fun `facade invocation flow invokes the call facade API`() {
        val flow = FacadeInvocationFlow()

        val mockFlowMessaging = mock<FlowMessaging>()
        whenever(mockFlowMessaging.callFacade(any(), any(), any(), any())).then { it.arguments[3] as String }

        flow.flowMessaging = mockFlowMessaging

        val requestBody = "Hello world!"
        val request = mock<RestRequestBody>()

        whenever(request.getRequestBody()).thenReturn(requestBody)

        val response = flow.call(request)

        assertThat(response).isEqualTo(requestBody)
    }
}
