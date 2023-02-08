package net.corda.libs.packaging.core

import net.corda.v5.crypto.SecureHash
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.time.Instant
import java.util.stream.Collectors
import net.corda.data.crypto.SecureHash as AvroSecureHash
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
 * @property fileChecksum The CPK file's checksum.
 * @constructor Create empty Cpk metadata
 */
data class CpkMetadata(
    val cpkId: CpkIdentifier,
    val manifest: CpkManifest,
    val mainBundle: String,
    val libraries: List<String>,
    val cordappManifest: CordappManifest,
    val type: CpkType,
    val fileChecksum: SecureHash,
    // TODO - is this needed here?
    val cordappCertificates: Set<Certificate>,
    val timestamp: Instant
) {
    companion object {
        fun fromAvro(other: CpkMetadataAvro): CpkMetadata {
            return CpkMetadata(
                CpkIdentifier.fromAvro(other.id),
                CpkManifest.fromAvro(other.manifest),
                other.mainBundle,
                other.libraries,
                CordappManifest.fromAvro(other.corDappManifest),
                CpkType.fromAvro(other.type),
                SecureHash(other.hash.algorithm, other.hash.bytes.array()),
                let {
                    val crtFactory = CertificateFactory.getInstance("X.509")
                    other.corDappCertificates.stream().map {
                        ByteArrayInputStream(it.array())
                            .use(crtFactory::generateCertificate)
                    }.collect(Collectors.toUnmodifiableSet())
                },
                other.timestamp
            )
        }

        fun fromJsonAvro(payload: String): CpkMetadata {
            val decoder = DecoderFactory.get().jsonDecoder(CpkMetadataAvro.`SCHEMA$`, payload)
            val avroObj = SpecificDatumReader<CpkMetadataAvro>(CpkMetadataAvro.`SCHEMA$`).read(null, decoder)
            return fromAvro(avroObj)
        }
    }

    fun isContractCpk() = cordappManifest.type == CordappType.CONTRACT

    // TODO - should we do these conversions back/forth or could this just be a proxy to the AVRO object itself?
    fun toAvro(): CpkMetadataAvro {
        return CpkMetadataAvro(
            cpkId.toAvro(),
            manifest.toAvro(),
            mainBundle,
            libraries,
            cordappManifest.toAvro(),
            type.toAvro(),
            AvroSecureHash(fileChecksum.algorithm, ByteBuffer.wrap(fileChecksum.bytes)),
            cordappCertificates.stream()
                .map(Certificate::getEncoded)
                .map(ByteBuffer::wrap)
                .collect(
                    Collectors.toUnmodifiableList()),
            timestamp
        )
    }

    fun toJsonAvro(): String {
        val avro = this.toAvro()
        // This is fairly generic and could be moved out if there is a usecase for it elsewhere
        SpecificDatumWriter<CpkMetadataAvro>(avro.schema).also { writer ->
            ByteArrayOutputStream().use { out ->
                val encoder = EncoderFactory.get().jsonEncoder(avro.schema, out)
                writer.write(avro, encoder)
                encoder.flush()
                return out.toString(Charsets.UTF_8)
            }
        }
    }
}
