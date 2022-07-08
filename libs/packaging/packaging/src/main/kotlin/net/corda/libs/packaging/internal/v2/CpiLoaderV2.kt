package net.corda.libs.packaging.internal.v2

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.CpkReader
import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.PackagingConstants.CPI_GROUP_POLICY_ENTRY
import net.corda.libs.packaging.certSummaryHash
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.internal.CpiImpl
import net.corda.libs.packaging.internal.CpiLoader
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.jar.JarInputStream

class CpiLoaderV2(private val clock: Clock = UTCClock()) : CpiLoader {

    override fun loadCpi(
        inputStream: InputStream,
        expansionLocation: Path,
        cpiLocation: String?,
        verifySignature: Boolean,
    ): Cpi {
        // Read input stream so we can process it multiple times
        val cpiBytes = inputStream.readAllBytes()

        // Calculate file hash
        val hash = calculateHash(cpiBytes)

        // Read CPI
        JarInputStream(cpiBytes.inputStream(), false).use { jarInputStream ->

            val cpiEntries = readJar(jarInputStream)

            val groupPolicy = cpiEntries.single { it.entry.name.endsWith(CPI_GROUP_POLICY_ENTRY) }
            val cpb = cpiEntries.single { it.entry.name.endsWith(".cpb") }

            // Read CPB
            val cpks = readCpb(cpb.bytes.inputStream(), expansionLocation, cpiLocation).toList()

            val mainAttributes = jarInputStream.manifest.mainAttributes
            return CpiImpl(
                CpiMetadata(
                    cpiId = CpiIdentifier(
                        mainAttributes.getValue(PackagingConstants.CPB_NAME_ATTRIBUTE)
                            ?: throw PackagingException("CPI name missing from manifest"),
                        mainAttributes.getValue(PackagingConstants.CPB_VERSION_ATTRIBUTE)
                            ?: throw PackagingException("CPI version missing from manifest"),
                        groupPolicy.entry.certificates.asSequence().certSummaryHash()
                    ),
                    fileChecksum = SecureHash(DigestAlgorithmName.SHA2_256.name, hash),
                    cpksMetadata = cpks.map { it.metadata },
                    groupPolicy = String(groupPolicy.bytes),
                    timestamp = clock.instant()
                ),
                cpks
            )
        }
    }

    private fun calculateHash(cpiBytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        md.update(cpiBytes)
        return md.digest()
    }

    private fun readCpb(cpb: InputStream, expansionLocation: Path, cpiLocation: String?): Sequence<Cpk> {
        return JarInputStream(cpb, false).use { cpbInputStream ->
            readJar(cpbInputStream)
                .map {
                    CpkReader.readCpk(
                        it.bytes.inputStream(),
                        expansionLocation,
                        cpkLocation = cpiLocation.plus("/${it.entry.name}"),
                        verifySignature = false,
                        cpkFileName = Paths.get(it.entry.name).fileName.toString()
                    )
                }
        }
    }
}
