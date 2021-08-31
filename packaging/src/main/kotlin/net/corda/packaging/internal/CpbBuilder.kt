package net.corda.packaging.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import net.corda.packaging.Cpk
import net.corda.packaging.DependencyResolutionException
import net.corda.packaging.SigningParameters
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.streams.asSequence

class CpbBuilder(
        private val cpkFiles: Iterable<Path>,
        private val cpkArchives: Iterable<Path>,
        metadataReader: Reader? = null,
        private val signingParams: SigningParameters?,
        private val useSignatures : Boolean) {

    private val metadata : Config = metadataReader?.use {
        ConfigFactory.parseReader(it, ConfigParseOptions.defaults())
    } ?: ConfigFactory.empty()

    private data class CpkData(val cpk: Cpk, val path : Path)

    private fun resolveDependencies(roots: Iterable<Path>, archivePaths: Iterable<Path>) : Iterable<Path> {
        val rootSet = roots.asSequence().map(Path::toRealPath).toSet()
        val index = TreeMap<Cpk.Identifier, CpkData>()
        index(roots, index)
        val rootIdentifiers = index.keys.toList()
        for(archivePath in archivePaths) {
            val cpkFiles = Files.list(archivePath).asSequence()
                .filter { it.toRealPath() !in rootSet }
                .filter { it.fileName.toString().toLowerCase().endsWith(Cpk.fileExtension) }
                .toList()
            index(cpkFiles, index)
        }
        val dependencyMap = index.entries.associateByTo(TreeMap(), {it.key}, {it.value.cpk.dependencies})
        return CpkDependencyResolver.resolveDependencies(rootIdentifiers, dependencyMap, useSignatures).map { index[it]!!.path }
    }

    private fun index(roots: Iterable<Path>,
                      existingIndex : NavigableMap<Cpk.Identifier, CpkData> = TreeMap()) : NavigableMap<Cpk.Identifier, CpkData> {
        for(root in roots) {
            val cpk = Cpk.Archived.from(Files.newInputStream(root), cpkLocation = root.toString(), useSignatures)
            val previous = existingIndex.put(cpk.id, CpkData(cpk, root))
            if(previous != null) throw DependencyResolutionException(
                    "Detected two CPKs with the same identifier ${cpk.id}: '$root' and '${previous.path}'")
        }
        return existingIndex
    }

    private fun writeArchive(output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val resolvedCpkSet = resolveDependencies(cpkFiles, cpkArchives)
        ZipOutputStream(output).use { os ->
            ZipTweaker.writeZipEntry(os, {
                metadata.root().render(ConfigRenderOptions.concise()).byteInputStream()
            }, "install.json", buffer, ZipEntry.DEFLATED)
            for (cpkFile in resolvedCpkSet) {
                ZipTweaker.writeZipEntry(os, { Files.newInputStream(cpkFile) }, cpkFile.fileName.toString(), buffer, ZipEntry.STORED)
            }
            val manifest = Manifest()
            val manifestBytes = ByteArrayOutputStream().use {
                manifest.write(it)
                it
            }.toByteArray()
            ZipTweaker.writeZipEntry(os, { ByteArrayInputStream(manifestBytes) }, "META-INF/MANIFEST.MF", buffer, ZipEntry.DEFLATED)
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