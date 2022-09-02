package net.corda.testing.sandboxes.packaging.internal

import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.CpkReader
import net.corda.libs.packaging.PackagingConstants.CPB_NAME_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPB_VERSION_ATTRIBUTE
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.internal.CpiLoader
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash

class CpbLoaderV2(private val clock: Clock = UTCClock()) : CpiLoader {

    override fun loadCpi(
        byteArray: ByteArray,
        expansionLocation: Path,
        cpiLocation: String?,
        verifySignature: Boolean
    ): Cpi {

        // Calculate file hash
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)

        JarInputStream(DigestInputStream(ByteArrayInputStream(byteArray), md), false).use {
            val mainAttributes = it.manifest.mainAttributes
            val cpks = mutableListOf<Cpk>()

            while (true) {
                val jarEntry = it.nextEntry ?: break
                if (isCpk(jarEntry)) {
                    val cpkBytes = it.readAllBytes()
                    val cpk = CpkReader.readCpk(
                        cpkBytes.inputStream(),
                        expansionLocation,
                        cpkLocation = cpiLocation.plus("/${jarEntry.name}"),
                        verifySignature = false,
                        cpkFileName = Paths.get(jarEntry.name).fileName.toString()
                    )
                    cpks.add(cpk)
                }
                it.closeEntry()
            }

            return object : Cpi {
                override val metadata =
                    CpiMetadata(
                        cpiId = CpiIdentifier(
                            mainAttributes.getValue(CPB_NAME_ATTRIBUTE)
                                ?: throw PackagingException("$CPB_NAME_ATTRIBUTE missing from CPB manifest"),
                            mainAttributes.getValue(CPB_VERSION_ATTRIBUTE)
                                ?: throw PackagingException("$CPB_VERSION_ATTRIBUTE missing from CPB manifest"),
                            null // signerSummaryHash comes from group policy file which is not present in CPB
                        ),
                        fileChecksum = SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest()),
                        cpksMetadata = cpks.map { it.metadata },
                        groupPolicy = null,
                        timestamp = clock.instant()
                    )
                override val cpks =
                    cpks

                private val cpksMap = cpks.associate { cpk ->
                    cpk.metadata.cpkId to cpk
                }
                override fun getCpkById(id: CpkIdentifier) =
                    cpksMap[id]
            }
        }
    }
}

private fun isCpk(zipEntry: ZipEntry) = zipEntry.name.endsWith(".jar")