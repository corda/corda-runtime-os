package net.corda.virtualnode.rest.impl.v1

import net.corda.data.ExceptionEnvelope
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.HttpApiException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
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
                    -> BadRequestException(exception.errorMessage)
                VirtualNodeAlreadyExistsException::class.java.name
                    -> ResourceAlreadyExistsException(exception.errorMessage)
                else
                    -> InternalServerException(exception.errorMessage)
            }
        }
    }
}