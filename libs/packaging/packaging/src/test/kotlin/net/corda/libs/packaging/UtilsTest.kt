package net.corda.libs.packaging

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.TestUtils.filterAndSortX500Attributes
import net.corda.libs.packaging.testutils.TestUtils
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.lang.IllegalStateException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

class UtilsTest {
    private companion object {
        fun mockCert(x500Name: String) =
            mock<X509Certificate>().also {
                val x500Principal = mock<X500Principal>().also { x500Principal ->
                    whenever(x500Principal.name).thenReturn(x500Name)
                }
                whenever(it.subjectX500Principal).thenReturn(x500Principal)
            }

        const val aliceX500Name = "CN=Alice,OU=R3,O=Corda,L=Dublin,C=IE"

        const val bobX500Name = "CN=Bob,OU=R3,O=Corda,L=Dublin,C=IE"

        val ALICE_CERT = certificate(
            "alice",
            this::class.java.classLoader.getResourceAsStream("alice.p12")
                ?: throw IllegalStateException("Resource alice.p12 not found")
        )

        val ALICE_V2_CERT = certificate(
            "alice",
            this::class.java.classLoader.getResourceAsStream("alice-v2.p12")
                ?: throw IllegalStateException("Resource alice-v2.p12 not found")
        )

        private fun certificate(alias: String, keyStoreInputStream: InputStream): X509Certificate {
            val privateKeyEntry = privateKeyEntry(alias, keyStoreInputStream)
            return privateKeyEntry.certificate as X509Certificate
        }

        private fun privateKeyEntry(alias: String, keyStoreInputStream: InputStream): KeyStore.PrivateKeyEntry {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStoreInputStream.use { keyStoreData -> keyStore.load(keyStoreData, TestUtils.KEY_STORE_PASSWORD) }
            return keyStore.getEntry(alias, KeyStore.PasswordProtection("cordadevpass".toCharArray())) as KeyStore.PrivateKeyEntry
        }
    }

    @Test
    fun `certSummaryHash takes X500 names of each signing certificate hashes them, sorts hashes and hashes over them`() {
        val aliceCert = mockCert(aliceX500Name)
        val bobCert = mockCert(bobX500Name)
        val certs = listOf(aliceCert, bobCert)

        val algoName = DigestAlgorithmName.SHA2_256.name
        val md = MessageDigest.getInstance(algoName)
        certs
            .map { filterAndSortX500Attributes(it.subjectX500Principal.name).toByteArray().hash() }
            .sortedWith(secureHashComparator)
            .map(SecureHash::toString)
            .map(String::toByteArray)
            .forEach {
                md.update(it)
            }
        val expectedCertSummaryHash = SecureHashImpl(algoName, md.digest())

        // Check X500 names hashes getting sorted before they get hashed
        val outOfOrderCerts = sequenceOf(bobCert, aliceCert)

        assertEquals(expectedCertSummaryHash, outOfOrderCerts.signerSummaryHash())
    }

    @Test
    fun `MemberX500Name_parse works as expected`() {
        assertEquals(
            "CN=Alice, OU=R3, O=Corda, L=Dublin, C=IE",
            MemberX500Name.parse(aliceX500Name).toString()
        )
    }

    @Test
    fun`same certificate but with different SERIALNUMBER gets same signer summary hash`() {
        val aliceX500Name = ALICE_CERT.subjectX500Principal.toString()
        val aliceV2X500Name = ALICE_V2_CERT.subjectX500Principal.toString()
        val commonX500Attributes = "CN=Alice, OU=R3, O=Corda, L=Dublin, C=IE, OID.1.3.6.1.4.1.311.60.2.1.3=GB, OID.2.5.4.15=Private"

        assertEquals(
            "$commonX500Attributes, SERIALNUMBER=10103259",
            aliceX500Name
        )
        assertEquals(
            "$commonX500Attributes, SERIALNUMBER=10103258",
            aliceV2X500Name
        )
        val aliceSignerSummaryHash = sequenceOf(ALICE_CERT).signerSummaryHash()
        val aliceV2SignerSummaryHash = sequenceOf(ALICE_V2_CERT).signerSummaryHash()
        assertEquals(aliceSignerSummaryHash, aliceV2SignerSummaryHash)
    }

    @Test
    fun `deterministically serializes X500 attributes`() {
        val aliceCert = mockCert("CN=Alice,OU=R3,O=Corda,L=Dublin,C=IE")
        val aliceCertDiffOrder = mockCert("C=IE, OU=R3, CN=Alice,O=Corda,L=Dublin")
        val aliceX500Name = aliceCert.subjectX500Principal.name
        val aliceV2X500Name = aliceCertDiffOrder.subjectX500Principal.name
        assertNotEquals(aliceX500Name, aliceV2X500Name)
        val aliceSignerSummaryHash = sequenceOf(aliceCert).signerSummaryHash()
        val aliceDiffOrderSignerSummaryHash = sequenceOf(aliceCertDiffOrder).signerSummaryHash()
        assertEquals(aliceSignerSummaryHash, aliceDiffOrderSignerSummaryHash)
    }
}
