package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CpkMetadata
import java.io.IOException
import java.nio.file.Path
import java.util.jar.JarFile

internal class CpkImpl(
    override val metadata: CpkMetadata,
    private val jarFile: JarFile,
    override val path: Path,
    override val originalFileName: String?
) : Cpk {

    override fun getResourceAsStream(resourceName: String) = jarFile.getJarEntry(resourceName)
        ?.let(jarFile::getInputStream)
        ?: throw IOException("Unknown resource $resourceName")

    override fun close() = jarFile.close()
}

