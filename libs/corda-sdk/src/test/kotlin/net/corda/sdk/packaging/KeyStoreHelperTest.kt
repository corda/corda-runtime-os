package net.corda.sdk.packaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyStore
import kotlin.io.path.createTempFile

class KeyStoreHelperTest {

    private val targetKeystore = createTempFile("myKeyStore", ".pfx").also {
        it.toFile().deleteOnExit()
    }
    private val keystoreAlias = "myAlias"
    private val keystorePassword = "pword"

    @Test
    fun generateKeyStoreTest() {
        KeyStoreHelper().generateKeyStore(
            keyStoreFile = targetKeystore.toFile(),
            alias = keystoreAlias,
            password = keystorePassword
        )
        assertThat(targetKeystore).isNotEmptyFile
    }

    @Test
    fun importCertificateIntoKeyStoreTest() {
        val ksh = KeyStoreHelper()
        val keystoreFile = targetKeystore.toFile()
        ksh.generateKeyStore(
            keyStoreFile = keystoreFile,
            alias = keystoreAlias,
            password = keystorePassword
        )

        ksh.importCertificateIntoKeyStore(
            keyStoreFile = keystoreFile,
            keyStorePassword = keystorePassword,
            certificateAlias = "foo",
            certificateInputStream = ksh.getDefaultGradleCertificateStream()
        )

        val keyStore = KeyStore.getInstance(KeyStoreHelper.KEYSTORE_INSTANCE_TYPE)
        keyStore.load(keystoreFile.inputStream(), keystorePassword.toCharArray())
        assertThat(keyStore.containsAlias("foo"))
    }

    @Test
    fun exportCertificateFromKeyStoreTest() {
        val ksh = KeyStoreHelper()
        val keystoreFile = targetKeystore.toFile()
        ksh.generateKeyStore(
            keyStoreFile = keystoreFile,
            alias = keystoreAlias,
            password = keystorePassword
        )

        ksh.importCertificateIntoKeyStore(
            keyStoreFile = keystoreFile,
            keyStorePassword = keystorePassword,
            certificateAlias = "foo",
            certificateInputStream = ksh.getDefaultGradleCertificateStream()
        )

        val targetCertFile = createTempFile("myCert", ".pem").also {
            it.toFile().deleteOnExit()
        }

        ksh.exportCertificateFromKeyStore(
            keyStoreFile = keystoreFile,
            keyStorePassword = keystorePassword,
            certificateAlias = keystoreAlias,
            exportedCertFile = targetCertFile.toFile()
        )
        assertThat(targetCertFile).isNotEmptyFile
        assertThat(targetCertFile).content().contains("BEGIN CERTIFICATE")
    }
}
