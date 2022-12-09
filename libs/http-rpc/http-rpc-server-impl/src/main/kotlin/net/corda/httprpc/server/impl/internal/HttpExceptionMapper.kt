package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.javalin.http.HttpResponseException
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.concurrent.TimeoutException
import javax.security.auth.login.FailedLoginException

internal object HttpExceptionMapper {

    fun mapToResponse(e: Exception): HttpResponseException {
        return when (e) {
            // the code has already thrown the appropriate Javalin response exception.
            is HttpResponseException -> e

            is MissingKotlinParameterException -> buildBadRequestResponse("Missing or invalid field in JSON request body.", e)
            is JsonProcessingException -> buildBadRequestResponse("Error during processing of request JSON.", e)

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
            is HttpApiException -> e.asHttpResponseException()

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

    private fun HttpApiException.asHttpResponseException(): HttpResponseException {
        return HttpResponseException(
            responseCode.statusCode,
            message,
            details.addResponseCode(responseCode)
        )
    }

    /**
     * Since Javalin's 'BadRequestResponse' does not allow extra details, we'll manually build the HttpResponseException with a BAD_REQUEST
     * status code, a message, and extra exception details that includes the original exception type and message to help the user fix their
     * request.
     * Unless details are already supplied by [HttpApiException].
     */
    private fun buildBadRequestResponse(message: String, e: Exception): HttpResponseException {
        return (e.cause as? HttpApiException)?.asHttpResponseException() ?: HttpResponseException(
            ResponseCode.BAD_REQUEST.statusCode,
            message,
            buildExceptionCauseDetails(e).addResponseCode(ResponseCode.BAD_REQUEST)
        )
    }

    /**
     * We'll add the name of the exception and the exception's message to the extra details map.
     * This will give the user extra information to resolving their issue.
     */
    private fun buildExceptionCauseDetails(e: Exception) = mapOf(
        "cause" to e::class.java.name,
        "reason" to (e.message ?: "")
    )

    /**
     * We'll add the code to the response.
     */
    private fun Map<String, String>.addResponseCode(responseCode: ResponseCode): Map<String, String> {
        val mutable = this.toMutableMap()
        mutable["code"] = responseCode.name
        return mutable
    }
}