package net.corda.chunking.db.impl.validation

import net.corda.chunking.ChunkReaderFactoryImpl
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.chunking.db.impl.persistence.PersistenceUtils.signerSummaryHashForDbQuery
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.verify.verifyCpi
import net.corda.membership.certificate.service.CertificatesService
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.schema.membership.MembershipSchema.GroupPolicySchema
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.versioning.Version
import net.corda.v5.crypto.SecureHash
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarInputStream


@Suppress("LongParameterList")
class CpiValidatorImpl constructor(
    private val publisher: StatusPublisher,
    private val chunkPersistence: ChunkPersistence,
    private val cpiPersistence: CpiPersistence,
    private val cpiInfoWriteService: CpiInfoWriteService,
    private val membershipSchemaValidator: MembershipSchemaValidator,
    private val cpiCacheDir: Path,
    private val cpiPartsDir: Path,
    private val certificatesService: CertificatesService,
    private val clock: Clock
) : CpiValidator {
    companion object {
        private val log = contextLogger()
        // TODO Certificate type should be define somewhere else with CORE-6130
        private const val CERTIFICATE_TYPE = "codesigner"
    }

    override fun validate(requestId: RequestId): SecureHash {
        //  Each function may throw a [ValidationException]
        log.debug("Validating $requestId")

        // Assemble the CPI locally and return information about it
        publisher.update(requestId, "Validating upload")
        val fileInfo = assembleFileFromChunks(cpiCacheDir, chunkPersistence, requestId, ChunkReaderFactoryImpl)

        publisher.update(requestId, "Verifying CPI")
        fileInfo.verifyCpi(getCerts(), requestId)

        publisher.update(requestId, "Validating CPI")
        val cpi: Cpi = fileInfo.validateAndGetCpi(cpiPartsDir, requestId)

        publisher.update(requestId, "Checking group policy is well formed.")
        try {
            membershipSchemaValidator.validateGroupPolicy(
                GroupPolicySchema.Default,
                Version(cpi.validateAndGetGroupPolicyFileVersion(), 0),
                cpi.metadata.groupPolicy!!
            )
        } catch (ex: MembershipSchemaValidationException) {
            throw ValidationException("Group policy file in the CPI is invalid. ${ex.message}", null, ex)
        }

        publisher.update(requestId, "Checking group id in CPI")
        val groupId = cpi.validateAndGetGroupId(requestId, GroupPolicyParser::groupIdFromJson)

        publisher.update(
            requestId, "Checking we can upsert a cpi with name=${cpi.metadata.cpiId.name} and groupId=$groupId"
        )

        cpiPersistence.validateCanUpsertCpi(
            cpiName = cpi.metadata.cpiId.name,
            cpiSignerSummaryHash = cpi.metadata.cpiId.signerSummaryHashForDbQuery,
            cpiVersion = cpi.metadata.cpiId.version,
            groupId = groupId,
            forceUpload = fileInfo.forceUpload,
            requestId = requestId
        )

        publisher.update(requestId, "Extracting Liquibase files from CPKs in CPI")
        val cpkDbChangeLogEntities = cpi.extractLiquibaseScripts()

        publisher.update(requestId, "Persisting CPI")
        val cpiMetadataEntity =
            cpiPersistence.persistCpiToDatabase(cpi, groupId, fileInfo, requestId, cpkDbChangeLogEntities, log)

        publisher.update(requestId, "Notifying flow workers")
        val cpiMetadata = CpiMetadata(
            cpi.metadata.cpiId,
            fileInfo.checksum,
            cpi.cpks.map { it.metadata },
            cpi.metadata.groupPolicy,
            version = cpiMetadataEntity.entityVersion,
            timestamp = clock.instant()
        )
        cpiInfoWriteService.put(cpiMetadata.cpiId, cpiMetadata)

        return fileInfo.checksum
    }

    /**
     * Retrieves trusted certificates for packaging verification
     */
    private fun getCerts(): Collection<X509Certificate> {
        val certs = certificatesService.retrieveAllCertificates(CERTIFICATE_TYPE)
        if (certs.isEmpty()) {
            log.warn("No trusted certificates for package validation found")
            return emptyList()
        }
        val certificateFactory = CertificateFactory.getInstance("X.509")
        return certs.map { certificateFactory.generateCertificate(it.byteInputStream()) as X509Certificate }
    }

    /**
     * Verifies CPI
     *
     * @throws ValidationException if CPI format > 1.0
     */
    private fun FileInfo.verifyCpi(certificates: Collection<X509Certificate>, requestId: String) {
        fun isCpiFormatV1() =
            try {
                val format = JarInputStream(Files.newInputStream(path)).use {
                    it.manifest.mainAttributes.getValue(PackagingConstants.CPI_FORMAT_ATTRIBUTE)
                }
                format == null || format == "1.0"
            } catch (t: Throwable) {
                false
            }

        try {
            verifyCpi(name, Files.newInputStream(path), certificates)
        } catch (ex: Exception) {
            if (isCpiFormatV1()) {
                log.warn("Error validating CPI. Ignoring error for format 1.0: ${ex.message}", ex)
            } else {
                throw ValidationException("Error validating CPI.  ${ex.message}", requestId)
            }
        }
    }
}
