package net.corda.chunking.db.impl.validation

import net.corda.chunking.ChunkReaderFactoryImpl
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.CpiPersistence
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.verify.verifyCpi
import net.corda.membership.certificate.service.CertificatesService
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


@Suppress("LongParameterList")
class CpiValidatorImpl constructor(
    private val publisher: StatusPublisher,
    private val chunkPersistence: ChunkPersistence,
    private val cpiPersistence: CpiPersistence,
    private val cpiInfoWriteService: CpiInfoWriteService,
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

        publisher.update(requestId, "Checking signatures")
        fileInfo.checkSignature()

        // The following bit in only just adds the verifyCpi call site to compile. Having said that:
        // - The following (cordadevcodesignpublic.pem) is the certificate of "cordadevcodesign.p12" (default)
        // used in `corda-gradle-plugins.cordapp-cpk` (defaulted CPB developer certificate).
        // - Normally we would need to load two certificates to verify a CPI, the CPB developer's and the network operator's (?).
        // - The following CPI verification is de-activated for now because does not work.
        // TODO "cpiVerificationEnabled" deactivation flag is to be removed once CPI verification works as per
        //  https://r3-cev.atlassian.net/browse/CORE-5407
        val cpiVerificationEnabled = System.getProperty("cpiVerificationEnabled", "false").toBoolean()
        if (cpiVerificationEnabled) {
            publisher.update(requestId, "Verifying CPI")
            // - The certificates are normally going to be loaded from the database.
            val certs = getCerts()
            verifyCpi(fileInfo.name, Files.newInputStream(fileInfo.path), certs)
        }

        publisher.update(requestId, "Validating CPI")
        val cpi: Cpi = fileInfo.validateAndGetCpi(cpiPartsDir)

        publisher.update(requestId, "Checking group id in CPI")
        val groupId = cpi.validateAndGetGroupId(GroupPolicyParser::groupIdFromJson)

        if (!fileInfo.forceUpload) {
            publisher.update(requestId, "Validating group id against DB")
            cpiPersistence.verifyGroupIdIsUniqueForCpi(cpi)
        }

        publisher.update(
            requestId, "Checking we can upsert a cpi with name=${cpi.metadata.cpiId.name} and groupId=$groupId"
        )
        canUpsertCpi(cpi, groupId, fileInfo.forceUpload)

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
     *  Check that we can upsert a CPI with the same name and group id, or a new cpi
     *  with a different name *and* different group id.  This is enforcing the policy
     *  of one CPI per mgm group id.
     */
    private fun canUpsertCpi(cpi: Cpi, groupId: String, forceUpload: Boolean) {
        if (!cpiPersistence.canUpsertCpi(
                cpi.metadata.cpiId.name,
                groupId,
                forceUpload,
                cpi.metadata.cpiId.version
            )
        ) {
            throw ValidationException(
                "Group id ($groupId) in use with another CPI.  " +
                        "Cannot upload ${cpi.metadata.cpiId.name} ${cpi.metadata.cpiId.version}"
            )
        }
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
}
