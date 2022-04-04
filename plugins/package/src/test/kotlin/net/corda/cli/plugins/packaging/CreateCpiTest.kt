package net.corda.cli.plugins.packaging

import jdk.security.jarsigner.JarSigner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class CreateCpiTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var testCpb: Path

    private val app = CreateCpi()
    private val testGroupPolicy = Path.of(this::class.java.getResource("/TestGroupPolicy.json")?.toURI()
        ?: error("TestGroupPolicy.json not found"))
    private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
        ?: error("signingkeys.pfx not found"))

    @BeforeEach
    fun setup() {
        testCpb = createCpb()
    }

    private fun createCpb(): Path {
        val tmpFile = Path.of(tempDir.toString(), "test.jar")
        val cpbFile = Path.of(tempDir.toString(), "test.cpb")

        try {
            // Build test CPB
            buildTestCpb(tmpFile)

            // Sign test CPB
            signTestCpb(tmpFile, cpbFile)
        } finally {
            Files.deleteIfExists(tmpFile)
        }

        return cpbFile
    }

    private fun buildTestCpb(tmpFile: Path) {
        val testContent = "TEST".toByteArray()
        JarOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE_NEW)).use { jar ->
            arrayOf(
                "META-INF/MANIFEST.MF",
                "test-cpk-1.cpk",
                "test-cpk-2.cpk"
            ).forEach { fileName ->
                jar.putNextEntry(ZipEntry(fileName))
                jar.write(testContent)
            }
        }
    }

    private fun signTestCpb(tmpFile: Path, cpbFile: Path) {
        val passwordCharArray = "keystore password".toCharArray()
        val keyEntry = KeyStore.getInstance(testKeyStore.toFile(), passwordCharArray).getEntry(
            "signing key 2",
            KeyStore.PasswordProtection(passwordCharArray)
        ) as? KeyStore.PrivateKeyEntry ?: error("Alias \"${"signing key 2"}\" is not a private key")

        ZipFile(tmpFile.toFile()).use { unsignedCpi ->
            Files.newOutputStream(
                cpbFile,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            ).use { signedCpi ->
                val certPath = CertificateFactory
                    .getInstance("X.509")
                    .generateCertPath(keyEntry.certificateChain.asList())

                // Sign jar
                JarSigner.Builder(keyEntry.privateKey, certPath)
                    .signerName("CORDAPP")
                    .build()
                    .sign(unsignedCpi, signedCpi)
            }
        }
    }

    private fun tmpOutputFile() = Path.of(tempDir.toString(), "output.cpi")

    @Test
    fun buildCpiLongParameterNames() {
        val outputFile = tmpOutputFile()

        val errText = captureStdErr {
            CommandLine(app).execute(
                "--cpb=${testCpb}",
                "--group-policy=${testGroupPolicy}",
                "--file=$outputFile",
                "--keystore=${testKeyStore}",
                "--storepass=keystore password",
                "--key=signing key 1",
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        checkCpi(outputFile)
    }

    @Test
    fun buildCpiShortParameterNames() {
        val outputFile = tmpOutputFile()

        val errText = captureStdErr {
            CommandLine(app).execute(
                "-c=${testCpb}",
                "-g=${testGroupPolicy}",
                "-f=$outputFile",
                "-s=${testKeyStore}",
                "-p=keystore password",
                "-k=signing key 1",
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        checkCpi(outputFile)
    }

    @Test
    fun buildCpiDefaultOutputFilename() {
        val defaultOutputTestCpb: Path = Path.of(tempDir.toString(), "DefaultOutputFilename.cpb")
        Files.copy(testCpb, defaultOutputTestCpb, StandardCopyOption.COPY_ATTRIBUTES)

        val errText = captureStdErr {
            CommandLine(app).execute(
                "-c=${defaultOutputTestCpb}",
                "-g=${testGroupPolicy}",
                "-s=${testKeyStore}",
                "-p=keystore password",
                "-k=signing key 1",
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        val outputFile = Path.of(tempDir.toString(), "DefaultOutputFilename.cpi")
        checkCpi(outputFile)
    }


    private fun checkCpi(outputFile: Path) {
        // Check files are present
        var groupPolicyPresent = false
        var manifestPresent = false
        var cpk1Present = false
        var cpk2Present = false
        var cpiSignaturePresent = false
        var cpbSignaturePresent = false
        JarInputStream(Files.newInputStream(outputFile), true).use {

            // Check manifest
            if (it.manifest.entries.isNotEmpty())
                manifestPresent = true

            // Check other files
            var entry = it.nextJarEntry
            while (entry != null) {
                when (entry.name) {
                    "META-INF/GroupPolicy.json" -> groupPolicyPresent = true
                    "test-cpk-1.cpk" -> cpk1Present = true
                    "test-cpk-2.cpk" -> cpk2Present = true
                    "META-INF/CPI-SIG.SF" -> cpiSignaturePresent = true
                    "META-INF/CORDAPP.SF" -> cpbSignaturePresent = true
                }

                entry = it.nextJarEntry
            }
        }

        // Check we saw all the files we expected to
        Assertions.assertAll(
            { assertTrue(groupPolicyPresent, "groupPolicyPresent") },
            { assertTrue(manifestPresent, "manifestPresent") },
            { assertTrue(cpk1Present, "cpk1Present") },
            { assertTrue(cpk2Present, "cpk2Present") },
            { assertTrue(cpiSignaturePresent, "cpiSignaturePresent") },
            { assertTrue(cpbSignaturePresent, "cpbSignaturePresent") },
        )
    }


    @Test
    fun testNoOptionError() {

        val errText = captureStdErr { CommandLine(app).execute("") }

        assertEquals("""Missing required options: '--cpb=<cpbFileName>', '--group-policy=<groupPolicyFileName>', '--keystore=<keyStoreFileName>', '--storepass=<keyStorePass>', '--key=<keyAlias>'
Usage: create -c=<cpbFileName> [-f=<outputFileName>] -g=<groupPolicyFileName>
              -k=<keyAlias> -p=<keyStorePass> -s=<keyStoreFileName>
              [-t=<tsaUrl>]
Creates a CPI from a CPB and GroupPolicy.json file.
  -c, --cpb=<cpbFileName>   CPB file to convert into CPI
  -f, --file=<outputFileName>
                            Output file
                            If omitted, the CPB filename with .cpi as a
                              filename extension is used
  -g, --group-policy=<groupPolicyFileName>
                            Group policy to include in CPI
                            Use "-" to read group policy from standard input
  -k, --key=<keyAlias>      Key alias
  -p, --password, --storepass=<keyStorePass>
                            Keystore password
  -s, --keystore=<keyStoreFileName>
                            Keystore holding siging keys
  -t, --tsa=<tsaUrl>        Time Stamping Authority (TSA) URL
""", errText)
    }

    private fun captureStdErr(target: () -> Unit): String {
        val original = System.err
        var outText = ""
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            System.setErr(PrintStream(byteArrayOutputStream))

            target()

            outText = byteArrayOutputStream.toString().replace(System.lineSeparator(), "\n")

        } finally {
            System.setErr(original)
            System.err.write(outText.toByteArray())
        }
        return outText
    }
}