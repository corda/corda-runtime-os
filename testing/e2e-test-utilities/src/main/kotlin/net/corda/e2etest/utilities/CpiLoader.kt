package net.corda.e2etest.utilities

import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.utilities.deleteRecursively
import net.corda.utilities.readAll
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.createTempDirectory

object CpiLoader {
    private fun getInputStream(resourceName: String): InputStream {
        return this::class.java.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")
    }

    fun get(
        cpbResourceName: String?,
        groupPolicy: String,
        cpiName: String,
        cpiVersion: String,
        signOptions: SignOptions = SignOptions(
            keyStore = "cordadevcodesign.p12",
            keyAlias = "cordacodesign",
            keyStorePassword = "cordacadevpass"
        )
    ) = cpbToCpi(cpbResourceName?.let { getInputStream(it) }, groupPolicy, cpiName, cpiVersion, signOptions)

    fun getRawResource(resourceName: String) = getInputStream(resourceName)

    /**
     * Returns a new input stream.
     * Don't use this method when we have actual CPIs
     */
    fun cpbToCpi(
        inputStream: InputStream?,
        networkPolicy: String,
        cpiNameValue: String,
        cpiVersionValue: String,
        signOptions: SignOptions
    ): InputStream {

        val tempDirectory = createTempDirectory()
        try {
            val cpbPath = inputStream?.let { input ->
                // Save CPB to disk
                val cpbPath = tempDirectory.resolve("cpb.cpb")
                Files.newOutputStream(cpbPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                    input.copyTo(it)
                }
                cpbPath
            }

            // Save group policy to disk
            val groupPolicyPath = tempDirectory.resolve("groupPolicy")
            Files.newBufferedWriter(groupPolicyPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                it.write(networkPolicy)
            }

            // Save keystore to disk
            val keyStorePath = tempDirectory.resolve(signOptions.keyStore)
            Files.newOutputStream(keyStorePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
                it.write(getKeyStore(signOptions.keyStore))
            }

            // Create CPI
            val cpiPath = tempDirectory.resolve("cpi")
            val exitCode = CreateCpiV2().apply {
                cpbPath?.let {
                    cpbFileName = it.toString()
                }
                cpiName = cpiNameValue
                cpiVersion = cpiVersionValue
                cpiUpgrade = false
                groupPolicyFileName = groupPolicyPath.toString()
                outputFileName = cpiPath.toString()
                signingOptions = SigningOptions().apply {
                    keyStoreFileName = keyStorePath.toString()
                    keyStorePass = signOptions.keyStorePassword
                    keyAlias = signOptions.keyAlias
                }
            }.call()

            assertEquals(0, exitCode, "Create CPI returned non-zero exit code")

            // Read CPI
            return cpiPath.readAll().inputStream()
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun getKeyStore(keyStoreResource: String) =
        javaClass.classLoader.getResourceAsStream(keyStoreResource)?.use { it.readAllBytes() }
            ?: throw FileNotFoundException("$keyStoreResource not found")
}

data class SignOptions(
    val keyStore: String,
    val keyAlias: String,
    val keyStorePassword: String,
    val signatureFile: String? = null,
    val timeStampingAuthorityUrl: String? = null,
)
