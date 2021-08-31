package net.corda.packaging.internal

import net.corda.v5.crypto.SecureHash
import net.corda.packaging.Cpb
import net.corda.packaging.Cpk
import net.corda.v5.crypto.DigestAlgorithmName
import java.io.InputStream
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry

object CpbLoader {
    private fun isCpk(entry : ZipEntry) = !entry.isDirectory && entry.name.endsWith(Cpk.fileExtension)

    @Suppress("NestedBlockDepth")
    fun from(inputStream : InputStream, expansionLocation : Path?, cpbLocation : String?, verifySignature : Boolean) : Cpb {
        val archivedCpks = mutableListOf<Cpk>()
        val expandedCpks = mutableListOf<Cpk.Expanded>()
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        JarInputStream(DigestInputStream(inputStream, md), verifySignature).use { zipInputStream ->
            while(true) {
                val entry = zipInputStream.nextEntry ?: break
                when {
                    isCpk(entry) -> {
                        /** We need to do this as [Cpk.Expanded.from] closes the stream, while we still need it afterward **/
                        val uncloseableInputStream = UncloseableInputStream(zipInputStream)
                        if(expansionLocation != null) {
                            expandedCpks += Cpk.Expanded.from(uncloseableInputStream, expansionLocation.resolve(entry.name),
                                    cpkLocation = cpbLocation?.plus("/${entry.name}"),
                                    verifySignature = verifySignature)
                        } else {
                            archivedCpks += Cpk.Archived.from(uncloseableInputStream,
                                    cpkLocation = cpbLocation?.plus("/${entry.name}"),
                                    verifySignature = verifySignature)
                        }
                    }
                }
                zipInputStream.closeEntry()
            }
        }
        return if(expansionLocation != null) {
            Cpb.Expanded(Cpb.Identifier(SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest())), Cpb.MetaData(mapOf()), cpks = expandedCpks)
        } else {
            Cpb.Archived(Cpb.Identifier(SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest())), Cpb.MetaData(mapOf()), cpks = archivedCpks)
        }
    }
}