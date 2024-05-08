package net.corda.blobinspector

import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppTest {
    @Test
    fun parseCashSignedTransaction() {
        run("cash-stx-db.blob")
    }

    @Test
    fun parseCashWireTransaction() {
        run("cash-wtx.blob")
    }

    @Test
    fun parseNetworkParameters() {
        run("network-parameters")
    }

    @Test
    fun parseNetworkNodeInfo() {
        run("node-info")
    }

    @Test
    fun parseOlderCpk() {
        run("OlderCpk.bin")
    }

    @Test
    fun parseUpgradingCpk() {
        run("UpgradingCpk.bin")
    }

    @Test
    fun parseC5WireTx() {
        run("C5WireTx.bin")
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

    private fun run(resourceName: String, includeOriginalBytes: Boolean = true) {
        val bytes = this.javaClass.getResourceAsStream(resourceName)?.readFully()?.sequence()
        requireNotNull(bytes) {
            "Couldn't read resource: $resourceName"
        }
        val decoded = Encoding.decodedBytes(bytes, includeOriginalBytes)
        println(decoded.result.prettyPrint())
    }

    private fun runInSeparateJVM(binaryFileName: String, flags: List<String> = emptyList()): Pair<Int, String> {
        val currentPath = System.getProperty("user.dir")
        val pathToBinary = "$currentPath/src/test/resources/net/corda/blobinspector/$binaryFileName"
        println("pathToBinary: $pathToBinary")

        // Check if the binary file exists
        val binaryFile = File(pathToBinary)
        if (!binaryFile.exists()) {
            throw FileNotFoundException("Binary file not found at path: $pathToBinary")
        }

        // Create a StringBuilder to capture the printed output
        val output = StringBuilder()

        // Create a ProcessBuilder to execute the JAR file
        val pathToBlobInspectorJar = "$currentPath/build/bin/corda-universal-blob-inspector-5.3.0.0-SNAPSHOT.jar"

        // Check if the JAR file exists
        val jarFile = File(pathToBlobInspectorJar)
        if (jarFile.exists()) {
            println("JAR file exists at path: $pathToBlobInspectorJar")
        } else {
            println("JAR file not found at path: $pathToBlobInspectorJar")
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

        println("Captured output: $output")

        return exitCode to output.toString()
    }
}
