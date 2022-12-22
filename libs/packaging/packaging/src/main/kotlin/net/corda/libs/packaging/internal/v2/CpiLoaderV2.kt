package net.corda.libs.packaging.internal.v2

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.CpkReader
import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.PackagingConstants.CPI_GROUP_POLICY_ENTRY
import net.corda.libs.packaging.signerSummaryHash
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.hash
import net.corda.libs.packaging.internal.CpiImpl
import net.corda.libs.packaging.internal.CpiLoader
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarInputStream

class CpiLoaderV2(private val clock: Clock = UTCClock()) : CpiLoader {

    override fun loadCpi(
        byteArray: ByteArray,
        expansionLocation: Path,
        cpiLocation: String?,
        verifySignature: Boolean,
    ): Cpi {

        // Calculate file hash
        val hash = calculateHash(byteArray)

        // Read CPI
        JarInputStream(byteArray.inputStream()).use { jarInputStream ->

            val cpiEntries = readJar(jarInputStream)

            val groupPolicy = cpiEntries.single { it.entry.name.endsWith(CPI_GROUP_POLICY_ENTRY) }

            val cpb =
                cpiEntries
                    .filter { it.entry.name.endsWith(".cpb") }
                    .run {
                        when (this.size) {
                            0 -> null
                            1 -> this[0]
                            else -> throw PackagingException("Multiple CPBs found in CPI.")
                        }
                    }

            // Read CPB
            val cpks = if (cpb != null) {
                readCpksFromCpb(cpb.bytes.inputStream(), expansionLocation, cpiLocation).toList()
            } else {
                emptyList()
            }

            val mainAttributes = jarInputStream.manifest.mainAttributes
            return CpiImpl(
                CpiMetadata(
                    cpiId = CpiIdentifier(
                        mainAttributes.getValue(PackagingConstants.CPI_NAME_ATTRIBUTE)
                            ?: throw PackagingException("CPI name missing from manifest"),
                        mainAttributes.getValue(PackagingConstants.CPI_VERSION_ATTRIBUTE)
                            ?: throw PackagingException("CPI version missing from manifest"),
                        groupPolicy.entry.certificates.asSequence().signerSummaryHash()
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

    private fun calculateHash(cpiBytes: ByteArray) = cpiBytes.hash(DigestAlgorithmName.SHA2_256).bytes

    private fun readCpksFromCpb(cpb: InputStream, expansionLocation: Path, cpiLocation: String?): List<Cpk> {
        return JarInputStream(cpb, false).use { cpbInputStream ->
            readJar(cpbInputStream)
                .filter { it.entry.name.endsWith(".jar") }
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
