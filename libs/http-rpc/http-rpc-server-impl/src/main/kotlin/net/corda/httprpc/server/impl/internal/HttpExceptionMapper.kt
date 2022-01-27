package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpResponseException
import io.javalin.http.InternalServerErrorResponse
import java.util.concurrent.TimeoutException
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.server.impl.exception.MissingParameterException
import net.corda.v5.application.flows.BadRpcStartFlowRequestException
import org.slf4j.LoggerFactory

internal object HttpExceptionMapper {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun mapToResponse(e: Exception): HttpResponseException {
        return when (e) {
            // the code has already thrown the appropriate Javalin response exception.
            is HttpResponseException -> e

            is BadRpcStartFlowRequestException -> BadRequestResponse("Operation failed due to bad RPC StartFlow request.")
            is MissingKotlinParameterException -> BadRequestResponse("Missing or invalid field in JSON request body.")
            is JsonProcessingException -> BadRequestResponse("Error during processing of request JSON.")
            is MissingParameterException -> BadRequestResponse(e.message)
            // TODO restore these when possible
            //  is StartFlowPermissionException -> ForbiddenResponse(loggedMessage)
            //  is FlowNotFoundException -> NotFoundResponse(loggedMessage)
            //  is InvalidCordaX500NameException -> BadRequestResponse(loggedMessage)
            //  is MemberNotFoundException -> NotFoundResponse(loggedMessage)

            // catch-all for Timeouts
            is TimeoutException -> InternalServerErrorResponse("Timeout occurred while processing operation.")

            // catch-all for IllegalArgumentExceptions
            is IllegalArgumentException -> HttpResponseException(
                ResponseCode.INTERNAL_SERVER_ERROR.statusCode,
                "Illegal argument occurred.",
                buildExceptionCauseDetails(e).addResponseCode(ResponseCode.INTERNAL_SERVER_ERROR)
            )

            // Http API exceptions
            is HttpApiException -> HttpResponseException(
                e.responseCode.statusCode,
                e.message,
                e.details.addResponseCode(e.responseCode)
            )

            else -> {
                logger.error("Unexpected error occurred and was unmapped by http exception mapper.", e)
                val message = e.message ?: "Unexpected error occurred."
                HttpResponseException(
                    ResponseCode.UNEXPECTED_ERROR.statusCode,
                    message,
                    buildExceptionCauseDetails(e).addResponseCode(ResponseCode.UNEXPECTED_ERROR)
                )
            }
        }
    }

    private fun buildExceptionCauseDetails(e: Exception) = mapOf(
        "cause" to e::javaClass.name,
        "reason" to (e.message ?: "")
    )

    private fun Map<String, String>.addResponseCode(responseCode: ResponseCode): Map<String, String> {
        val mutable = this.toMutableMap()
        mutable["code"] = responseCode.name
        return mutable
    }
}