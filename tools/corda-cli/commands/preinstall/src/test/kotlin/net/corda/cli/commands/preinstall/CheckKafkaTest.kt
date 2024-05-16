package net.corda.cli.commands.preinstall

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import picocli.CommandLine


class CheckKafkaTest {

    @Test
    fun testKafkaFileParsing() {
        val path = "./src/test/resources/KafkaTestBadConnection.yaml"
        val kafka = CheckKafka()
        val ret = CommandLine(kafka).execute(path)

        assertEquals(1, ret)
    }
}