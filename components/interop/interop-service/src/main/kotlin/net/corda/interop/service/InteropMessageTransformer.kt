package net.corda.interop.service

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.data.interop.InteropMessage
import net.corda.interop.data.FacadeRequest
import net.corda.interop.data.FacadeResponse

class InteropMessageTransformer {
    companion object {
        fun getFacadeRequest(message: InteropMessage): FacadeRequest {
            val mapper = ObjectMapper()
            return mapper.readValue(message.payload, FacadeRequest::class.java)
        }

        fun getFacadeResponse(message: InteropMessage): FacadeResponse {
            val mapper = ObjectMapper()
            return mapper.readValue(message.payload, FacadeResponse::class.java)
        }

        fun getInteropMessage(
            messageId: String,
            request: FacadeRequest
        ): InteropMessage {
            val mapper = ObjectMapper()
            return InteropMessage(messageId, mapper.writeValueAsString(request))
        }
    }

}