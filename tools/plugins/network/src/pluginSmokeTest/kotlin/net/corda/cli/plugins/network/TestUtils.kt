package net.corda.cli.plugins.network

import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal object TestUtils {
    fun captureStdErr(block: () -> Int): Pair<String, Int> {
        val original = System.err
        var outText = ""
        val exitCode = try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            System.setErr(PrintStream(byteArrayOutputStream))

            val exitCode = block()

            outText = byteArrayOutputStream.toString().replace(System.lineSeparator(), "\n")
            exitCode
        } finally {
            System.setErr(original)
            System.err.write(outText.toByteArray())
        }
        return outText to exitCode
    }
}
