package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonProcessingException
import io.javalin.http.BadRequestResponse
import io.javalin.http.GatewayTimeoutResponse
import io.javalin.http.HttpResponseException
import java.util.concurrent.TimeoutException
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.server.impl.exception.MissingParameterException
import net.corda.utilities.rootCause
import net.corda.utilities.rootMessage
import net.corda.v5.application.flows.BadRpcStartFlowRequestException

class HttpExceptionMapper {

    companion object {
        @JvmStatic
        @Suppress("ThrowsCount", "ComplexMethod")
        fun mapToResponse(e: Exception): HttpResponseException {
            return when (e) {
                // the code has already thrown the appropriate Javalin response exception.
                is HttpResponseException -> e

                is BadRpcStartFlowRequestException -> e.message?.let { BadRequestResponse(it) } ?: BadRequestResponse()
                is JsonProcessingException -> e.message?.let { BadRequestResponse(it) } ?: BadRequestResponse()
                is MissingParameterException -> BadRequestResponse(e.message)
                // TODO restore these when possible
                //  is StartFlowPermissionException -> ForbiddenResponse(loggedMessage)
                //  is FlowNotFoundException -> NotFoundResponse(loggedMessage)
                //  is InvalidCordaX500NameException -> BadRequestResponse(loggedMessage)
                //  is MemberNotFoundException -> NotFoundResponse(loggedMessage)

                // catch-all for TimeoutExceptions responds with GatewayTimeoutResponse
                is TimeoutException -> e.message?.let { GatewayTimeoutResponse(it) } ?: GatewayTimeoutResponse()

                // catch-all for IllegalArgumentException responds with BadRequestResponse
                is IllegalArgumentException -> e.message?.let { BadRequestResponse(it) } ?: BadRequestResponse()

                // Http API exceptions
                is HttpApiException -> HttpResponseException(
                    e.responseCode.statusCode,
                    e.message ?: "Unknown error occurred.",
                    e.details ?: emptyMap()
                )

                else -> {
                    with(mutableMapOf<String, String>()) {
                        this["exception"] = e.toString()
                        this["rootCause"] = e.rootCause.toString()
                        e.rootMessage?.let { this["rootMessage"] = it }
                        HttpResponseException(
                            ResponseCode.INTERNAL_SERVER_ERROR.statusCode,
                            e.message ?: "Internal error occurred.",
                            this
                        )
                    }
                }
            }
        }
    }
}