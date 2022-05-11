package net.corda.libs.packaging

import net.corda.data.packaging.CpkMetadata as AvroCpkMetadata
import net.corda.data.crypto.SecureHash as AvroSecureHash
import net.corda.packaging.Cpk
import net.corda.packaging.CordappManifest
import net.corda.packaging.converters.toAvro
import net.corda.packaging.converters.toCorda
import net.corda.v5.crypto.SecureHash
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.time.Instant
import java.util.stream.Collectors

// TODO - clean up CPI/CPK in net.corda.packaging

/**
 * Represents a CPK file in the cluster
 *
 * @property cpkId
 * @property manifest The file name of the main bundle inside the [Cpk]
 * @property mainBundle
 * @property libraries
 * @property dependencies
 * @property cordappManifest
 * @property type
 * @property fileChecksum
 * @property checksum The CPK file's checksum.
 * @constructor Create empty Cpk metadata
 */
data class CpkMetadata(
    val cpkId: CpkIdentifier,
    val manifest: Cpk.Manifest,
    val mainBundle: String,
    val libraries: List<String>,
    val dependencies: List<CpkIdentifier>,
    val cordappManifest: CordappManifest,
    val type: Cpk.Type,
    val fileChecksum: SecureHash,
    // TODO - is this needed here?
    val cordappCertificates: Set<Certificate>
) {
    companion object {
        fun fromAvro(other: AvroCpkMetadata): CpkMetadata {
            return CpkMetadata(
                CpkIdentifier.fromAvro(other.id),
                other.manifest.toCorda(),
                other.mainBundle,
                other.libraries,
                other.dependencies.map { CpkIdentifier.fromAvro(it) },
                other.corDappManifest.toCorda(),
                other.type.toCorda(),
                SecureHash(other.hash.algorithm, other.hash.serverHash.array()),
                let {
                    val crtFactory = CertificateFactory.getInstance("X.509")
                    other.corDappCertificates.stream().map {
                        ByteArrayInputStream(it.array())
                            .use(crtFactory::generateCertificate)
                    }.collect(Collectors.toUnmodifiableSet())

                }
            )
        }

        // TODO - remove when refactoring complete
        fun fromLegacyCpk(cpk: Cpk): CpkMetadata {
            return CpkMetadata(
                CpkIdentifier(cpk.metadata.id.name, cpk.metadata.id.version, cpk.metadata.id.signerSummaryHash),
                cpk.metadata.manifest,
                cpk.metadata.mainBundle,
                cpk.metadata.libraries,
                cpk.metadata.dependencies.map {
                    CpkIdentifier(it.name, it.version, it.signerSummaryHash)
                },
                cpk.metadata.cordappManifest,
                cpk.metadata.type,
                cpk.metadata.hash,
                cpk.metadata.cordappCertificates
            )
        }
    }

    fun toAvro(): AvroCpkMetadata {
        return AvroCpkMetadata(
            cpkId.toAvro(),
            manifest.toAvro(),
            mainBundle,
            libraries,
            dependencies.map { it.toAvro() },
            cordappManifest.toAvro(),
            type.toAvro(),
            AvroSecureHash(fileChecksum.algorithm, ByteBuffer.wrap(fileChecksum.bytes)),
            cordappCertificates.stream()
                .map(Certificate::getEncoded)
                .map(ByteBuffer::wrap)
                .collect(
                    Collectors.toUnmodifiableList()),
            Instant.now()
        )
    }
}
