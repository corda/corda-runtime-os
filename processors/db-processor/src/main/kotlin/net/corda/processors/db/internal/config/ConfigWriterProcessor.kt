package net.corda.processors.db.internal.config

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.processors.db.internal.db.DBWriter
import net.corda.v5.base.util.contextLogger
import javax.persistence.RollbackException

internal class ConfigWriterProcessor(private val dbWriter: DBWriter) : DurableProcessor<String, String> {
    private companion object {
        private val logger = contextLogger()
    }

    override val keyClass = String::class.java
    override val valueClass = String::class.java

    override fun onNext(events: List<StringRecord>) : List<StringRecord> {
        // TODO - Joel - We just grab the key and value here. We should establish the actual message format.
        // TODO - Joel - Have Avro schema.
        // TODO - Joel - Don't default record value to empty string. Handle properly.
        val configEntities = events.map { record -> ConfigEntity(record.key, record.value ?: "") }

        try {
            dbWriter.writeConfig(configEntities)
        } catch (e: RollbackException) {
            // TODO - Joel - Retry? Push back onto queue?
        } catch (e: Exception) {
            // These are exceptions related to incorrect set-up of the transaction, and should not occur.
            throw ConfigWriteException("TODO - Joel - Exception message.", e)
        }

        val outgoingRecords = events.map { record -> Record(CONFIG_TOPIC, record.key, record.value) }
        logger.info("JJJ publishing records $outgoingRecords") // TODO - Joel - Only logging for demo purposes here.
        return outgoingRecords
    }
}