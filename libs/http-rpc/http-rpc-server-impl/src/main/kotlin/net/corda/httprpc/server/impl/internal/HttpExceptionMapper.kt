package net.corda.httprpc.server.impl.internal

import io.javalin.http.HttpResponseException
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.server.impl.context.ContextUtils.buildExceptionCauseDetails
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.concurrent.TimeoutException
import javax.security.auth.login.FailedLoginException

internal object HttpExceptionMapper {

    fun mapToResponse(e: Exception): HttpResponseException {
        return when (e) {
            // the code has already thrown the appropriate Javalin response exception.
            is HttpResponseException -> e

            // catch-all for failed login attempts
            is FailedLoginException -> UnauthorizedResponse("User authentication failed.")

            // catch-all for Timeouts
            is TimeoutException -> InternalServerErrorResponse("Timeout occurred while processing operation.")

            // catch-all for IllegalArgumentExceptions
            is IllegalArgumentException -> HttpResponseException(
                ResponseCode.INTERNAL_SERVER_ERROR.statusCode,
                "Illegal argument occurred.",
                buildExceptionCauseDetails(e)
            )

            // Http API exceptions
            is HttpApiException -> e.asHttpResponseException()

            is CordaRuntimeException -> HttpResponseException(
                ResponseCode.INTERNAL_SERVER_ERROR.statusCode,
                "Internal server error.",
                buildExceptionCauseDetails(e)
            )

            else -> HttpResponseException(
                ResponseCode.UNEXPECTED_ERROR.statusCode,
                "Unexpected error occurred.",
                buildExceptionCauseDetails(e)
            )
        }
    }

    private fun HttpApiException.asHttpResponseException(): HttpResponseException {
        return HttpResponseException(responseCode.statusCode, message, details)
    }
}