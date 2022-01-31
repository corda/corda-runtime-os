package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpResponseException
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import java.util.concurrent.TimeoutException
import javax.security.auth.login.FailedLoginException
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.server.impl.exception.MissingParameterException
import net.corda.v5.application.flows.BadRpcStartFlowRequestException
import net.corda.v5.base.exceptions.CordaRuntimeException

internal object HttpExceptionMapper {

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

            // catch-all for failed login attempts
            is FailedLoginException -> UnauthorizedResponse("User authentication failed.")

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

            is CordaRuntimeException -> HttpResponseException(
                ResponseCode.INTERNAL_SERVER_ERROR.statusCode,
                "Internal server error.",
                buildExceptionCauseDetails(e).addResponseCode(ResponseCode.INTERNAL_SERVER_ERROR)
            )

            else -> HttpResponseException(
                ResponseCode.UNEXPECTED_ERROR.statusCode,
                "Unexpected error occurred.",
                buildExceptionCauseDetails(e).addResponseCode(ResponseCode.UNEXPECTED_ERROR)
            )
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