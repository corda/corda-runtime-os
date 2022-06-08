package net.corda.libs.packaging.internal

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.CpkReader
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.internal.PackagingConstants.CPI_GROUP_POLICY_ENTRY
import net.corda.libs.packaging.internal.PackagingConstants.CPI_NAME_ATTRIBUTE
import net.corda.libs.packaging.internal.PackagingConstants.CPI_VERSION_ATTRIBUTE
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

internal object CpiLoader {
    private const val cpkFileExtension = ".cpk"
    private fun isCpk(entry : ZipEntry) = !entry.isDirectory && entry.name.endsWith(cpkFileExtension)
    private fun isGroupPolicy(entry : ZipEntry) = !entry.isDirectory && entry.name.endsWith(CPI_GROUP_POLICY_ENTRY)

    fun loadCpi(inputStream : InputStream, expansionLocation : Path, cpiLocation : String?, verifySignature : Boolean) : Cpi {
        val ctx = load(inputStream, expansionLocation, cpiLocation, verifySignature)
        return CpiImpl(ctx.metadata, ctx.cpks!!)
    }

    private class CpiContext(val metadata : CpiMetadata, val cpks : List<Cpk>?)

    @Suppress("NestedBlockDepth", "ComplexMethod")
    private fun load(inputStream : InputStream, expansionLocation : Path?, cpiLocation : String?, verifySignature : Boolean) : CpiContext {
        val cpks = mutableListOf<Cpk>()
        val cpkMetadata = mutableListOf<CpkMetadata>()
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)

        var name : String? = null
        var version : String? = null
        val signatureCollector = SignatureCollector()

        var groupPolicy : String? = null
        JarInputStream(DigestInputStream(inputStream, md), verifySignature).use { jarInputStream ->
            jarInputStream.manifest?.let(Manifest::getMainAttributes)?.let { mainAttributes ->
                name = mainAttributes.getValue(CPI_NAME_ATTRIBUTE)
                version = mainAttributes.getValue(CPI_VERSION_ATTRIBUTE)
            }
            while(true) {
                val entry = jarInputStream.nextJarEntry ?: break
                if(verifySignature) signatureCollector.addEntry(entry)
                when {
                    isCpk(entry) -> {
                        /** We need to do this as [Cpk.from] closes the stream, while we still need it afterward **/
                        val uncloseableInputStream = UncloseableInputStream(jarInputStream)
                        if(expansionLocation != null) {
                            val cpk = CpkReader.readCpk(uncloseableInputStream,
                                expansionLocation,
                                cpkLocation = cpiLocation.plus("/${entry.name}"),
                                verifySignature = verifySignature,
                                cpkFileName = Paths.get(entry.name).fileName.toString()
                            )
                            cpks += cpk
                            cpkMetadata += cpk.metadata
                        } else {
                            cpkMetadata += CpkLoader.loadMetadata(uncloseableInputStream,
                                cpkLocation = cpiLocation?.plus("/${entry.name}"),
                                verifySignature = verifySignature)
                        }
                    }
                    isGroupPolicy(entry) -> {
                        groupPolicy = jarInputStream.reader().readText()
                    }
                }
                jarInputStream.closeEntry()
            }
        }

        return CpiContext(CpiMetadata(
            cpiId = CpiIdentifier(
                name ?: throw PackagingException("CPI name missing from manifest"),
                version ?: throw PackagingException("CPI version missing from manifest"),
                signatureCollector.certificates.asSequence().certSummaryHash()),
            fileChecksum = SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest()),
            cpksMetadata = cpkMetadata,
            groupPolicy = groupPolicy,
            timestamp = Instant.now()
        ), cpks.takeIf { expansionLocation != null } )
    }
}
