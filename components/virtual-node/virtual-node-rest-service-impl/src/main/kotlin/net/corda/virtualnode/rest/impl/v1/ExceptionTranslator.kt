package net.corda.virtualnode.rest.impl.v1

import net.corda.data.ExceptionEnvelope
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.HttpApiException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ResourceAlreadyExistsException
import org.slf4j.LoggerFactory

class ExceptionTranslator {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        /**
         * Translates [exception] to [HttpApiException]
         * */
        fun translate(exception: ExceptionEnvelope?): HttpApiException {
            if (exception == null) {
                logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
                return InternalServerException("Request was unsuccessful but no exception was provided.")
            }
            logger.warn("Remote request to create virtual node responded with exception: ${exception.errorMessage}")
            return when (exception.errorType) {
                IllegalArgumentException::class.java.name,
                CpiNotFoundException::class.java.name
                -> BadRequestException(
                    title = BadRequestException::class.java.simpleName,
                    exceptionDetails = ExceptionDetails(exception.errorType, exception.errorMessage)
                )

                VirtualNodeAlreadyExistsException::class.java.name
                -> ResourceAlreadyExistsException(
                    title = VirtualNodeAlreadyExistsException::class.java.simpleName,
                    exceptionDetails = ExceptionDetails(exception.errorType, exception.errorMessage)
                )

                else
                -> InternalServerException(
                    exceptionDetails = ExceptionDetails(
                        exception.errorType,
                        exception.errorMessage
                    )
                )
            }
        }
    }
}
