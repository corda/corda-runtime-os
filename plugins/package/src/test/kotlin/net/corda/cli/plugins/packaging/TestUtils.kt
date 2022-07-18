package net.corda.cli.plugins.packaging

import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal object TestUtils {

    fun captureStdErr(target: () -> Unit): String {
        val original = System.err
        var outText = ""
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            System.setErr(PrintStream(byteArrayOutputStream))

            target()

            outText = byteArrayOutputStream.toString().replace(System.lineSeparator(), "\n")

        } finally {
            System.setErr(original)
            System.err.write(outText.toByteArray())
        }
        return outText
    }
}