package net.corda.sdk.packaging

import net.corda.libs.packaging.verify.PackageType
import net.corda.libs.packaging.verify.VerifierBuilder
import net.corda.libs.packaging.verify.internal.VerifierFactory
import net.corda.sdk.packaging.signing.CertificateLoader
import net.corda.sdk.packaging.signing.SigningHelpers
import net.corda.sdk.packaging.signing.SigningOptions
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.cert.X509Certificate
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * Creates a CPI v2 from a CPB and GroupPolicy.json file.
 */
object CreateCpiV2 {
    /**
     * Filename of group policy within jar file
     */
    const val META_INF_GROUP_POLICY_JSON = "META-INF/GroupPolicy.json"

    const val MANIFEST_VERSION = "1.0"
    const val CPI_FORMAT_ATTRIBUTE = "2.0"

    val cpiFormatAttributeName = Attributes.Name("Corda-CPI-Format")
    val cpiNameAttributeName = Attributes.Name("Corda-CPI-Name")
    val cpiVersionAttributeName = Attributes.Name("Corda-CPI-Version")
    val cpiUpgradeAttributeName = Attributes.Name("Corda-CPI-Upgrade")

    /**
     * @throws IllegalArgumentException if it fails to verify Cpb V2
     */
    fun verifyIsValidCpbV2(cpbPath: Path, signingOptions: SigningOptions) {
        val trustedCerts = with(signingOptions) { CertificateLoader.readCertificates(keyStoreFileName, keyStorePass) }
        verifyIsValidCpbV2(cpbPath, trustedCerts)
    }

    /**
     * @throws IllegalArgumentException if it fails to verify Cpb V2
     */
    fun verifyIsValidCpbV2(cpbPath: Path, trustedCerts: Collection<X509Certificate>) {
        VerifierBuilder()
            .type(PackageType.CPB)
            .format(VerifierFactory.FORMAT_2)
            .name(cpbPath.toString())
            .inputStream(FileInputStream(cpbPath.toString()))
            .trustedCerts(trustedCerts)
            .build()
            .verify()
    }

    /**
     * Build and sign CPI file
     *
     * Creates a temporary file, copies CPB into temporary file, adds group policy then signs
     */
    fun buildAndSignCpi(
        cpbPath: Path?,
        outputFilePath: Path,
        groupPolicy: String,
        cpiAttributes: CpiAttributes,
        signingOptions: SigningOptions
    ) {
        val unsignedCpi = Files.createTempFile("buildCPI", null)
        try {
            buildUnsignedCpi(cpbPath, unsignedCpi, groupPolicy, cpiAttributes)
            SigningHelpers.sign(unsignedCpi, outputFilePath, signingOptions)
        } finally {
            Files.deleteIfExists(unsignedCpi)
        }
    }

    /**
     * Build unsigned CPI file
     *
     * Copies CPB into new jar file and then adds group policy
     */
    private fun buildUnsignedCpi(cpbPath: Path?, unsignedCpi: Path, groupPolicy: String, cpiAttributes: CpiAttributes) {
        val manifest = Manifest()
        val manifestMainAttributes = manifest.mainAttributes
        manifestMainAttributes[Attributes.Name.MANIFEST_VERSION] = MANIFEST_VERSION
        manifestMainAttributes[cpiFormatAttributeName] = CPI_FORMAT_ATTRIBUTE
        manifestMainAttributes[cpiNameAttributeName] = cpiAttributes.cpiName
        manifestMainAttributes[cpiVersionAttributeName] = cpiAttributes.cpiVersion
        manifestMainAttributes[cpiUpgradeAttributeName] = cpiAttributes.cpiUpgrade.toString()

        JarOutputStream(Files.newOutputStream(unsignedCpi, StandardOpenOption.WRITE), manifest).use { cpiJar ->
            cpbPath?.let {
                // Copy the CPB contents
                cpiJar.putNextEntry(JarEntry(cpbPath.fileName.toString()))
                Files.newInputStream(cpbPath, StandardOpenOption.READ).use {
                    it.copyTo(cpiJar)
                }
            }

            // Add group policy
            addGroupPolicy(cpiJar, groupPolicy)
        }
    }

    /**
     * Adds group policy file to jar file
     *
     * Reads group policy from stdin or file depending on user choice
     */
    private fun addGroupPolicy(cpiJar: JarOutputStream, groupPolicy: String) {
        cpiJar.putNextEntry(JarEntry(META_INF_GROUP_POLICY_JSON))
        cpiJar.write(groupPolicy.toByteArray())
        cpiJar.closeEntry()
    }
}
