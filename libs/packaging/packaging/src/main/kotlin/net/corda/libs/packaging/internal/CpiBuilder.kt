package net.corda.libs.packaging.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.DependencyResolutionException
import net.corda.libs.packaging.SigningParameters
import net.corda.libs.packaging.internal.PackagingConstants.CPI_GROUP_POLICY_ENTRY
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
internal class CpiBuilder(
        private val name : String,
        private val version : String,
        private val cpkFiles: Iterable<Path>,
        private val cpkArchives: Iterable<Path>,
        metadataReader: Reader? = null,
        private val signingParams: SigningParameters?,
        private val useSignatures : Boolean,
        private val groupPolicy: String?
) {

    private val metadata : Config = metadataReader?.use {
        ConfigFactory.parseReader(it, ConfigParseOptions.defaults())
    } ?: ConfigFactory.empty()

    private data class CpkData(val cpkMetadata: Cpk.Metadata, val path : Path)

    private fun resolveDependencies(roots: Iterable<Path>, archivePaths: Iterable<Path>) : Iterable<Path> {
        val rootSet = roots.asSequence().map(Path::toRealPath).toSet()
        val index = TreeMap<Cpk.Identifier, CpkData>()
        index(roots, index)
        val rootIdentifiers = index.keys.toList()
        for (archivePath in archivePaths) {
            val cpkFiles = Files.list(archivePath).use { stream ->
                stream.asSequence()
                    .filter { it.toRealPath() !in rootSet }
                    .filter { it.fileName.toString().lowercase().endsWith(Cpk.fileExtension) }
                    .toList()
            }
            index(cpkFiles, index)
        }
        val dependencyMap = index.entries.associateByTo(TreeMap(), {it.key}, {it.value.cpkMetadata.dependencies})
        return CpkDependencyResolver.resolveDependencies(rootIdentifiers, dependencyMap, useSignatures).map { index[it]!!.path }
    }

    private fun index(roots: Iterable<Path>,
                      existingIndex : NavigableMap<Cpk.Identifier, CpkData> = TreeMap()) : NavigableMap<Cpk.Identifier, CpkData> {
        for(root in roots) {
            val cpk = Files.newInputStream(root).use { Cpk.Metadata.from(it, cpkLocation = root.toString(), useSignatures) }
            val previous = existingIndex.put(cpk.id, CpkData(cpk, root))
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

            if (groupPolicy != null) {
                val groupPolicyEntry = ZipEntry(CPI_GROUP_POLICY_ENTRY).apply { method = ZipEntry.DEFLATED }
                os.putNextEntry(groupPolicyEntry)
                os.write(groupPolicy.toByteArray())
                os.closeEntry()
            }

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
