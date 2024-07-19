package net.corda.cli.plugins.network

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream
import kotlin.math.min
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

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
