package net.corda.httprpc.messageBus

import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import org.slf4j.Logger

object MessageBusUtils {

    fun <T> withServiceUnavailableOnRepartition(logger: Logger, opName: String, block: () -> T): T {
        return try {
            block()
        } catch (e: CordaRPCAPIPartitionException) {
            "Error on $opName due to repartitioning event".let {
                logger.warn(it, e)
                throw ServiceUnavailableException(it)
            }
        }
    }
}