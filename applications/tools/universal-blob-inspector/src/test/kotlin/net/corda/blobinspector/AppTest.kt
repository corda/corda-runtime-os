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
    fun parseMetadataV0() {
        run("metadatav0.bin")
    }

    @Test
    fun parseMetadataV1() {
        run("metadatav0.bin", buildMetadataV1 = true)
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

    private fun run(resourceName: String, includeOriginalBytes: Boolean = true, buildMetadataV1: Boolean = false) {
        val bytes = this.javaClass.getResourceAsStream(resourceName)?.readFully()?.sequence()
        requireNotNull(bytes) {
            "Couldn't read resource: $resourceName"
        }
        val finalBytes = if (buildMetadataV1) {
            ("corda".toByteArray() + byteArrayOf(8, 0, 1) + bytes.bytes).sequence()
        } else {
            bytes
        }
        val decoded = Encoding.decodedBytes(finalBytes, includeOriginalBytes)
        println(decoded.result.prettyPrint())
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
