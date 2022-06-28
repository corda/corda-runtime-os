package net.corda.chunking.db.impl.validation

import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.utilities.time.Clock
import net.corda.libs.packaging.verify.verifyCpi
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.chunking.db.impl.persistence.CpiPersistence

@Suppress("LongParameterList")
class CpiValidatorImpl constructor(
    private val publisher: StatusPublisher,
    chunkPersistence: ChunkPersistence,
    cpiPersistence: CpiPersistence,
    private val cpiInfoWriteService: CpiInfoWriteService,
    cpiCacheDir: Path,
    cpiPartsDir: Path,
    private val clock: Clock
) : CpiValidator {
    companion object {
        private val log = contextLogger()
    }

    private val validationFunctions = ValidationFunctions(cpiCacheDir, cpiPartsDir, chunkPersistence, cpiPersistence)

    override fun validate(requestId: RequestId): SecureHash {
        //  Each function may throw a [ValidationException]
        log.debug("Validating $requestId")

        // Assemble the CPI locally and return information about it
        publisher.update(requestId, "Validating upload")
        val fileInfo = validationFunctions.getFileInfo(requestId)

        publisher.update(requestId, "Checking signatures")
        validationFunctions.checkSignature(fileInfo)

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
        val cpi: Cpi = validationFunctions.checkCpi(fileInfo)

        publisher.update(requestId, "Checking group id in CPI")
        validationFunctions.getGroupId(cpi)

        if (!fileInfo.forceUpload) {
            publisher.update(requestId, "Validating group id against DB")
            validationFunctions.checkGroupIdDoesNotExistForThisCpi(cpi)
        }

        publisher.update(requestId, "Extracting Liquibase files from CPKs in CPI")
        val cpkDbChangeLogEntities = validationFunctions.extractLiquibaseScriptsFromCpi(cpi)

        publisher.update(requestId, "Persisting CPI")
        val cpiMetadataEntity = validationFunctions.persistToDatabase(
            cpi,
            fileInfo,
            requestId,
            cpkDbChangeLogEntities
        )

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

    // TODO The implementation of this method needs updating to load needed certificates from the database.
    //  It currently just loads the default certificate as a loaded resource whose private key is used at CPB signing
    //  in `corda-gradle-plugins.cordapp-cpk`.
    private fun getCerts(): Collection<X509Certificate> {
        val certs = mutableSetOf<X509Certificate>()

        val defaultCertificate = "cordadevcodesignpublic.pem"
        val keyStoreInputStream = this::class.java.classLoader.getResourceAsStream(defaultCertificate)
            ?: throw FileNotFoundException("Resource file \"$defaultCertificate\" not found")

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreInputStream.use { keyStore.load(it, "cordacadevpass".toCharArray()) }
        certs.add(keyStore.getCertificate("cordacodesign") as X509Certificate)
        return certs
    }
}
