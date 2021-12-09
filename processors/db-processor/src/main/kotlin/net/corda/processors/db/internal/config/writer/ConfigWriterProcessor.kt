package net.corda.processors.db.internal.config.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.processors.db.internal.db.DBWriter
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture
import javax.persistence.RollbackException

internal class ConfigWriterProcessor(
    private val dbWriter: DBWriter,
    private val publisher: Publisher
) : RPCResponderProcessor<PermissionManagementRequest, PermissionManagementResponse> {

    private companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        request: PermissionManagementRequest,
        respFuture: CompletableFuture<PermissionManagementResponse>
    ) {
        logger.info("JJJ got this request: $request")

        // TODO - Joel - Create actual Avro classes for requests and responses. Currently, we just stuff the
        //  key and value into the request user ID.
        val (key, value) = request.requestUserId.split('=')
        val configEntity = ConfigEntity(key, value)

        try {
            dbWriter.writeConfig(listOf(configEntity))
        } catch (e: RollbackException) {
            // TODO - Joel - Retry? Push back onto queue?
        } catch (e: Exception) {
            // These are exceptions related to incorrect set-up of the transaction, and should not occur.
            throw ConfigWriteServiceException("TODO - Joel - Exception message.", e)
        }

        logger.info("JJJ publishing records $configEntity") // TODO - Joel - This logging is only for demo purposes.
        // TODO - Joel - Send proper response.
        respFuture.complete(PermissionManagementResponse("DONT_CARE"))

        val record = Record(CONFIG_TOPIC, key, value)
        publisher.publish(listOf(record))
    }
}