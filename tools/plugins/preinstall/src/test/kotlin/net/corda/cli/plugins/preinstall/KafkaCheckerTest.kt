package net.corda.cli.plugins.preinstall

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import picocli.CommandLine


class KafkaCheckerTest {

    @Test
    fun testKafkaFileParsing() {
        val path = "./src/test/resources/KafkaTestBadConnection.yaml"
        val kafka = CheckKafka()
        val ret = CommandLine(kafka).execute(path)

        assertEquals(1, ret)
    }
}