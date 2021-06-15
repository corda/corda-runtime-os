package net.corda.comp.kafka.config.read

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigUpdate

class ReadConfigUpdate(private val kafkaConfigRead: KafkaConfigRead) : ConfigUpdate {

    override fun onUpdate(updatedConfig: Map<String, Config>) {
        kafkaConfigRead.snapshotReceived()
    }
}