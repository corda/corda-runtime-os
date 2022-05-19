package net.corda.libs.packaging.core

import net.corda.v5.crypto.SecureHash
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.stream.Collectors
import net.corda.data.packaging.CpkMetadata as CpkMetadataAvro

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
    val manifest: CpkManifest,
    val mainBundle: String,
    val libraries: List<String>,
    val dependencies: List<CpkIdentifier>,
    val cordappManifest: CordappManifest,
    val type: CpkType,
    val fileChecksum: SecureHash,
    // TODO - is this needed here?
    val cordappCertificates: Set<Certificate>
) {
    companion object {
        fun fromAvro(other: CpkMetadataAvro): CpkMetadata {
            return CpkMetadata(
                CpkIdentifier.fromAvro(other.id),
                CpkManifest.fromAvro(other.manifest),
                other.mainBundle,
                other.libraries,
                other.dependencies.map { CpkIdentifier.fromAvro(it) },
                CordappManifest.fromAvro(other.corDappManifest),
                CpkType.fromAvro(other.type),
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
    }

    fun toAvro(): CpkMetadataAvro {
        return CpkMetadataAvro(
            cpkId.toAvro(),
            manifest.toAvro(),
            mainBundle,
            libraries,
            dependencies.map { it.toAvro() },
            cordappManifest.toAvro(),
            type.toAvro(),
            net.corda.data.crypto.SecureHash(fileChecksum.algorithm, ByteBuffer.wrap(fileChecksum.bytes)),
            cordappCertificates.stream()
                .map(Certificate::getEncoded)
                .map(ByteBuffer::wrap)
                .collect(
                    Collectors.toUnmodifiableList())
        )
    }
}
