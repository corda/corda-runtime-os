package net.corda.packaging.internal

import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.packaging.PackagingException
import net.corda.packaging.internal.PackagingConstants.CPI_GROUP_POLICY_ENTRY
import net.corda.packaging.internal.PackagingConstants.CPI_NAME_ATTRIBUTE
import net.corda.packaging.internal.PackagingConstants.CPI_VERSION_ATTRIBUTE
import net.corda.packaging.util.UncloseableInputStream
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

internal object CPILoader {
    private fun isCPK(entry : ZipEntry) = !entry.isDirectory && entry.name.endsWith(CPK.fileExtension)
    private fun isGroupPolicy(entry : ZipEntry) = !entry.isDirectory && entry.name.endsWith(CPI_GROUP_POLICY_ENTRY)

    fun loadMetadata(inputStream : InputStream, cpiLocation : String?, verifySignature : Boolean) : CPI.Metadata {
        return load(inputStream, null, cpiLocation, verifySignature).metadata
    }

    fun loadCPI(inputStream : InputStream, expansionLocation : Path, cpiLocation : String?, verifySignature : Boolean) : CPI {
        val ctx = load(inputStream, expansionLocation, cpiLocation, verifySignature)
        return CPIImpl(ctx.metadata, ctx.cpks!!)
    }

    private class CPIContext(val metadata : CPI.Metadata, val cpks : List<CPK>?)

    @Suppress("NestedBlockDepth", "ComplexMethod")
    private fun load(inputStream : InputStream, expansionLocation : Path?, cpiLocation : String?, verifySignature : Boolean) : CPIContext {
        val cpks = mutableListOf<CPK>()
        val cpkMetadata = mutableListOf<CPK.Metadata>()
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
                    isCPK(entry) -> {
                        /** We need to do this as [CPK.from] closes the stream, while we still need it afterward **/
                        val uncloseableInputStream = UncloseableInputStream(jarInputStream)
                        if(expansionLocation != null) {
                            val cpk = CPK.from(
                                uncloseableInputStream,
                                expansionLocation,
                                cpkLocation = cpiLocation.plus("/${entry.name}"),
                                verifySignature = verifySignature,
                                cpkFileName = Paths.get(entry.name).fileName.toString()
                            )
                            cpks += cpk
                            cpkMetadata += cpk.metadata
                        } else {
                            cpkMetadata += CPK.Metadata.from(uncloseableInputStream,
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

        return CPIContext(CPIMetadataImpl(
            id = CPIIdentifierImpl(
                name ?: throw PackagingException("CPI name missing from manifest"),
                version ?: throw PackagingException("CPI version missing from manifest"),
                signatureCollector.certificates.asSequence().certSummaryHash()),
            hash = SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest()),
            cpks = cpkMetadata,
            groupPolicy = groupPolicy
        ), cpks.takeIf { expansionLocation != null } )
    }
}
