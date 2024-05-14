package net.corda.blobinspector

import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppTest {

    @Test
    fun parseCashSignedTransaction() {
        val(exitCode, output) = runInSeparateJVM("cash-stx-db.blob")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Corda OS 4+ / ENT 3+ AMQP"))
    }

    @Test
    fun parseCashWireTransaction() {
        val(exitCode, output) = runInSeparateJVM("cash-wtx.blob")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Corda OS 4+ / ENT 3+ AMQP"))
    }

    @Test
    fun parseNetworkParameters() {
        val(exitCode, output) = runInSeparateJVM("network-parameters")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Corda OS 4+ / ENT 3+ AMQP"))
    }

    @Test
    fun parseNetworkNodeInfo() {
        val(exitCode, output) = runInSeparateJVM("node-info")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Corda OS 4+ / ENT 3+ AMQP"))
    }

    @Test
    fun parseOlderCpk() {
        val(exitCode, output) = runInSeparateJVM("OlderCpk.bin")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Corda 5 GA AMQP"))
    }

    @Test
    fun parseUpgradingCpk() {
        val(exitCode, output) = runInSeparateJVM("UpgradingCpk.bin")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Corda 5 GA AMQP"))
    }

    @Test
    fun parseC5WireTx() {
        val(exitCode, output) = runInSeparateJVM("C5WireTx.bin")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Corda 5 GA AMQP"))
    }

    @Test
    fun parseMetadataV0() {
        val(exitCode, output) = runInSeparateJVM("metadatav0.bin")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Metadata schema version = 00"))
    }

    @Test
    fun parseMetadataV1() {
        val(exitCode, output) = runInSeparateJVM("metadatav1.bin")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("Metadata schema version = 01"))
    }

    @Test
    fun runWithoutBytes() {
        val(exitCode, output) = runInSeparateJVM("C5WireTx.bin", listOf("-b"))
        assertTrue(exitCode == 0)
        assertFalse(output.contains("_bytes"))
    }

    @Test
    fun runWithBytes() {
        val(exitCode, output) = runInSeparateJVM("C5WireTx.bin")
        assertTrue(exitCode == 0)
        assertTrue(output.contains("_bytes"))
    }

    private fun runInSeparateJVM(binaryFileName: String, flags: List<String> = emptyList()): Pair<Int, String> {
        val currentPath = System.getProperty("user.dir")
        val jarVersion = System.getProperty("JARVersion")
        val pathToBinary = "$currentPath/src/test/resources/net/corda/blobinspector/$binaryFileName"

        // Check if the binary file exists
        val binaryFile = File(pathToBinary)
        require(binaryFile.exists()) {
            "Binary file not found at path: $pathToBinary"
        }

        // Create a StringBuilder to capture the printed output
        val output = StringBuilder()

        // Create a ProcessBuilder to execute the JAR file
        val pathToBlobInspectorJar = "$currentPath/build/bin/corda-universal-blob-inspector-$jarVersion.jar"

        // Check if the JAR file exists
        val jarFile = File(pathToBlobInspectorJar)

        require(jarFile.exists()) {
            "JAR file not found at path: $pathToBlobInspectorJar"
        }

        val args = listOf("java", "-jar", pathToBlobInspectorJar, pathToBinary) + flags
        val processBuilder = ProcessBuilder(args)

        val process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }

        // Wait for the process to complete
        val exitCode = process.waitFor()

        return exitCode to output.toString()
    }
}
