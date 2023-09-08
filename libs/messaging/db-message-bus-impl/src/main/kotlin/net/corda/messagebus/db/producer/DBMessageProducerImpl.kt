package net.corda.messagebus.db.producer

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messagebus.api.producer.CordaMessage
import net.corda.messagebus.api.producer.MessageProducer
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.serialization.MessageHeaderSerializer
import net.corda.messagebus.db.util.WriteOffsets

class DBMessageProducerImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val dbAccess: DBAccess,
    private val writeOffets: WriteOffsets,
    private val headerSerializer: MessageHeaderSerializer,
    private val throwOnSerializationError: Boolean = true
) : MessageProducer {
    override fun send(message: CordaMessage<*>, callback: MessageProducer.Callback?) {
        TODO("Not yet implemented")
    }

    override fun sendMessages(messages: List<CordaMessage<*>>) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}