package net.corda.comp.kafka.config.read

import com.typesafe.config.Config
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(immediate = true, service = [KafkaConfigRead::class])
class KafkaConfigRead @Activate constructor(
    @Reference(service = ConfigReadServiceFactory::class)
    private val readServiceFactory: ConfigReadServiceFactory

) {
    private companion object{
        private val logger: Logger = LoggerFactory.getLogger(KafkaConfigRead::class.java)
    }

    var receivedSnapshot = false

    private val configReadService = readServiceFactory.createReadService()

    fun startReader() {
        configReadService.start()
        configReadService.registerCallback(ReadConfigUpdate(this))
    }

    fun getAllConfiguration(): Map<String, Config> {
        return configReadService.getAllConfiguration()
    }

    fun getConfiguration(key: String): Config {
        return configReadService.getConfiguration(key)
    }

    fun isReady(): Boolean {
        return receivedSnapshot
    }

    fun snapshotReceived() {
        receivedSnapshot = true
    }

}
