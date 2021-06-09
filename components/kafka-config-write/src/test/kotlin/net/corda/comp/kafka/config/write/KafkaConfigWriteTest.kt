package net.corda.comp.kafka.config.write

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import net.corda.libs.configuration.write.ConfigWriteService
import net.corda.libs.configuration.write.factory.ConfigWriteServiceFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.BufferedReader
import java.util.*

class KafkaConfigWriteTest {

    private lateinit var kafkaConfigWrite: KafkaConfigWrite
    private val configWriteServiceFactory: ConfigWriteServiceFactory = mock()
    private val configWriteService: ConfigWriteService = mock()

    @BeforeEach
    fun beforeEach() {
        kafkaConfigWrite = KafkaConfigWrite(configWriteServiceFactory)
    }


    @Test
    fun testCorrectConfig() {
        val configReader = BufferedReader(this::class.java.classLoader.getResourceAsStream("config.conf").reader())
        val configString = configReader.readText()
        configReader.close()
        Mockito.`when`(configWriteServiceFactory.createWriteService(any(), any()))
            .thenReturn(configWriteService)
        kafkaConfigWrite.updateConfig("dummyTopic", Properties(), configString)

        verify(configWriteService, times(2)).updateConfiguration(any(), any())
    }

    @Test
    fun testNestedConfig() {
        val configReader =
            BufferedReader(this::class.java.classLoader.getResourceAsStream("nestedConfig.conf").reader())
        val configString = configReader.readText()
        configReader.close()
        Mockito.`when`(configWriteServiceFactory.createWriteService(any(), any()))
            .thenReturn(configWriteService)
        kafkaConfigWrite.updateConfig("dummyTopic", Properties(), configString)

        verify(configWriteService, times(2)).updateConfiguration(any(), any())
    }

    @Test
    fun testDoubleConfig() {
        val configReader =
            BufferedReader(this::class.java.classLoader.getResourceAsStream("doubleConfig.conf").reader())
        val configString = configReader.readText()
        configReader.close()
        Mockito.`when`(configWriteServiceFactory.createWriteService(any(), any()))
            .thenReturn(configWriteService)
        kafkaConfigWrite.updateConfig("dummyTopic", Properties(), configString)

        verify(configWriteService, times(4)).updateConfiguration(any(), any())
    }

    @Test
    fun testBadConfig() {
        val configReader =
            BufferedReader(this::class.java.classLoader.getResourceAsStream("badConfig.conf").reader())
        val configString = configReader.readText()
        configReader.close()
        Mockito.`when`(configWriteServiceFactory.createWriteService(any(), any()))
            .thenReturn(configWriteService)
        kafkaConfigWrite.updateConfig("dummyTopic", Properties(), configString)

        verify(configWriteService, times(2)).updateConfiguration(any(), any())
    }
}