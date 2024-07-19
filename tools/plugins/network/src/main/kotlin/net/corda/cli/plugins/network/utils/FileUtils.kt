package net.corda.cli.plugins.network.utils

import net.corda.v5.base.util.EncodingUtils.toBase64
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

fun Collection<File>.hash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    this.forEach { file ->
        digest.update(file.readBytes())
    }
    // Replace characters '/', '+', '=' with '.', '-', '_' respectively to make the returned hash filename-safe.
    return digest.digest().let(::toBase64).replace('/', '.').replace('+', '-').replace('=', '_')
}

fun inferCpiName(cpbFile: File, groupPolicyFile: File): String {
    val combinedHash = listOf(cpbFile, groupPolicyFile).hash()
    return "${cpbFile.name}-$combinedHash"
}

/**
 * Check file exists and is readable.
 */
fun requireFileExists(file: File) {
    require(Files.isReadable(file.toPath())) { "\"$file\" does not exist or is not readable" }
}
