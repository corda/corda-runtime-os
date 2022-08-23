package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpkMetadata
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.jar.JarFile

internal class CpkImpl(
    override val metadata: CpkMetadata,
    override val path: Path,
    override val originalFileName: String?,
    private val jarFile: File,
    private val verifySignature: Boolean
) : Cpk {
    override fun getInputStream() = FileInputStream(jarFile)

    override fun getResourceAsStream(resourceName: String) = JarFile(jarFile, verifySignature).use { file ->
        file.getJarEntry(resourceName)
            ?.let { jarEntry ->
                file.getInputStream(jarEntry).use {
                    it.readAllBytes().inputStream()
                }
            } ?: throw IOException("Unknown resource $resourceName")
    }
}

