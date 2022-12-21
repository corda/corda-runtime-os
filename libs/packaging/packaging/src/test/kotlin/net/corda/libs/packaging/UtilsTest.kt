package net.corda.libs.packaging

import net.corda.v5.base.types.MemberX500Name
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
    }

    @Test
    fun `certSummaryHash takes X500 names of each signing certificate hashes them, sorts hashes and hashes over them`() {
        val aliceCert = mockCert(aliceX500Name)
        val bobCert = mockCert(bobX500Name)
        val certs = listOf(aliceCert, bobCert)

        val algoName = DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name
        val md = MessageDigest.getInstance(algoName)
        certs
            .map { MemberX500Name.parse(it.subjectX500Principal.name).toString().toByteArray().hash() }
            .sortedWith(secureHashComparator)
            .map(SecureHash::toString)
            .map(String::toByteArray)
            .forEach {
                md.update(it)
            }
        val expectedCertSummaryHash = SecureHash(algoName, md.digest())

        // Check X500 names hashes getting sorted before they get hashed
        val outOfOrderCerts = setOf(bobCert, aliceCert)

        assertEquals(expectedCertSummaryHash, outOfOrderCerts.signerSummaryHash())
    }

    @Test
    fun `MemberX500Name_parse works as expected`() {
        assertEquals(
            "CN=Alice, OU=R3, O=Corda, L=Dublin, C=IE",
            MemberX500Name.parse(aliceX500Name).toString()
        )
    }
}