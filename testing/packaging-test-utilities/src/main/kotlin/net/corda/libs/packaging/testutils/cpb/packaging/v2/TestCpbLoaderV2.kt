package net.corda.libs.packaging.testutils.cpb.packaging.v2

import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Arrays
import java.util.jar.JarEntry
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
import net.corda.libs.packaging.hash
import net.corda.libs.packaging.signerSummaryHash
import net.corda.utilities.time.Clock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash

internal class TestCpbLoaderV2(private val clock: Clock) {

    @Suppress("ComplexMethod", "NestedBlockDepth", "ThrowsCount")
    fun loadCpi(
        byteArray: ByteArray,
        expansionLocation: Path,
        cpiLocation: String?
    ): Cpi {


        // Calculate file hash
        val hash = calculateHash(byteArray)

        JarInputStream(ByteArrayInputStream(byteArray), true).use { jarInputStream ->
            val mainAttributes = jarInputStream.manifest.mainAttributes
            val cpks = mutableListOf<Cpk>()

            var firstCpkEntry: JarEntry? = null

            while (true) {
                val jarEntry = jarInputStream.nextJarEntry ?: break
                if (isCpk(jarEntry)) {
                    val cpkBytes = jarInputStream.readAllBytes()
                    val cpk = CpkReader.readCpk(
                        cpkBytes.inputStream(),
                        expansionLocation,
                        cpkLocation = cpiLocation.plus("/${jarEntry.name}"),
                        verifySignature = false,
                        cpkFileName = Paths.get(jarEntry.name).fileName.toString()
                    )
                    cpks.add(cpk)

                    jarInputStream.closeEntry()
                    if (firstCpkEntry == null) {
                        firstCpkEntry = jarEntry
                    } else {
                        if (!Arrays.equals(firstCpkEntry.codeSigners, jarEntry.codeSigners)) {
                            // TODO enrich exception message with signers
                            throw IllegalStateException("Mismatch in signers between ${firstCpkEntry.name} and ${jarEntry.name}")
                        }
                    }
                } else {
                    jarInputStream.closeEntry()
                }
            }

            requireNotNull(firstCpkEntry) { "No Cpks found in Cpb" }
            requireNotNull(firstCpkEntry.certificates) { "No certificates found for $firstCpkEntry" }

            return object : Cpi {
                override val metadata =
                    CpiMetadata(
                        cpiId = CpiIdentifier(
                            mainAttributes.getValue(CPB_NAME_ATTRIBUTE)
                                ?: throw PackagingException("$CPB_NAME_ATTRIBUTE missing from CPB manifest"),
                            mainAttributes.getValue(CPB_VERSION_ATTRIBUTE)
                                ?: throw PackagingException("$CPB_VERSION_ATTRIBUTE missing from CPB manifest"),
                                firstCpkEntry.certificates.asSequence().signerSummaryHash()
                        ),
                        fileChecksum = SecureHash(DigestAlgorithmName.SHA2_256.name, hash),
                        cpksMetadata = cpks.map { it.metadata },
                        groupPolicy = "{}",
                        timestamp = clock.instant()
                    )
                override val cpks =
                    cpks

                private val cpksMap = cpks.associateBy { cpk ->
                    cpk.metadata.cpkId
                }
                override fun getCpkById(id: CpkIdentifier) =
                    cpksMap[id]
            }
        }
    }
}

private fun calculateHash(cpiBytes: ByteArray) = cpiBytes.hash(DigestAlgorithmName.SHA2_256).bytes

private fun isCpk(zipEntry: ZipEntry) = zipEntry.name.endsWith(".jar")