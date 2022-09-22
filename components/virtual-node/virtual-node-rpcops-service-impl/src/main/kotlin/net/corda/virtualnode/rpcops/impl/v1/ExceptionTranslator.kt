package net.corda.virtualnode.rpcops.impl.v1

import net.corda.data.ExceptionEnvelope
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.HttpApiException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.v5.base.util.contextLogger

class ExceptionTranslator {
    companion object {
        private val logger = contextLogger()

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