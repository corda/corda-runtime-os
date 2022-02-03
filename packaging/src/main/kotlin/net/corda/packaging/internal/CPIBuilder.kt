package net.corda.packaging.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import net.corda.packaging.CPK
import net.corda.packaging.DependencyResolutionException
import net.corda.packaging.SigningParameters
import net.corda.packaging.util.ZipTweaker
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.NavigableMap
import java.util.TreeMap
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

@Suppress("LongParameterList")
internal class CPIBuilder(
        private val name : String,
        private val version : String,
        private val cpkFiles: Iterable<Path>,
        private val cpkArchives: Iterable<Path>,
        metadataReader: Reader? = null,
        private val signingParams: SigningParameters?,
        private val useSignatures : Boolean) {

    private val metadata : Config = metadataReader?.use {
        ConfigFactory.parseReader(it, ConfigParseOptions.defaults())
    } ?: ConfigFactory.empty()

    private data class CPKData(val cpkMetadata: CPK.Metadata, val path : Path)

    private fun resolveDependencies(roots: Iterable<Path>, archivePaths: Iterable<Path>) : Iterable<Path> {
        val rootSet = roots.asSequence().map(Path::toRealPath).toSet()
        val index = TreeMap<CPK.Identifier, CPKData>()
        index(roots, index)
        val rootIdentifiers = index.keys.toList()
        for(archivePath in archivePaths) {
            val cpkFiles = Files.list(archivePath).asSequence()
                .filter { it.toRealPath() !in rootSet }
                .filter { it.fileName.toString().toLowerCase().endsWith(CPK.fileExtension) }
                .toList()
            index(cpkFiles, index)
        }
        val dependencyMap = index.entries.associateByTo(TreeMap(), {it.key}, {it.value.cpkMetadata.dependencies})
        return CPKDependencyResolver.resolveDependencies(rootIdentifiers, dependencyMap, useSignatures).map { index[it]!!.path }
    }

    private fun index(roots: Iterable<Path>,
                      existingIndex : NavigableMap<CPK.Identifier, CPKData> = TreeMap()) : NavigableMap<CPK.Identifier, CPKData> {
        for(root in roots) {
            val cpk = Files.newInputStream(root).use { CPK.Metadata.from(it, cpkLocation = root.toString(), useSignatures) }
            val previous = existingIndex.put(cpk.id, CPKData(cpk, root))
            if(previous != null) throw DependencyResolutionException(
                    "Detected two CPKs with the same identifier ${cpk.id}: '$root' and '${previous.path}'")
        }
        return existingIndex
    }

    private fun writeArchive(output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        ZipOutputStream(output).use { os ->
            val manifestEntry = ZipEntry(JarFile.MANIFEST_NAME).apply {
                method = ZipEntry.DEFLATED
            }
            os.putNextEntry(manifestEntry)
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name(PackagingConstants.CPI_NAME_ATTRIBUTE)] = name
                mainAttributes[Attributes.Name(PackagingConstants.CPI_VERSION_ATTRIBUTE)] = version
            }.write(os)
            os.closeEntry()

            val resolvedCPKSet = resolveDependencies(cpkFiles, cpkArchives)
            ZipTweaker.writeZipEntry(os, {
                metadata.root().render(ConfigRenderOptions.concise()).byteInputStream()
            }, "install.json", buffer, ZipEntry.DEFLATED)
            for (cpkFile in resolvedCPKSet) {
                ZipTweaker.writeZipEntry(os, { Files.newInputStream(cpkFile) }, cpkFile.fileName.toString(), buffer, ZipEntry.STORED)
            }
        }
    }

    fun build(output: OutputStream) {
        if (signingParams == null) {
            writeArchive(output)
        } else {
            Files.createTempFile(null, null).also { tempFile ->
                try {
                    writeArchive(BufferedOutputStream(Files.newOutputStream(tempFile)))
                    SigningParameters.sign(tempFile.toFile(), output, signingParams)
                } finally {
                    Files.delete(tempFile)
                }
            }
        }
    }
}
