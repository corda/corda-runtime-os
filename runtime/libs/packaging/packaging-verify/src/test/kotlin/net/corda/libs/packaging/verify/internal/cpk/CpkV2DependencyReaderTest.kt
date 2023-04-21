package net.corda.libs.packaging.verify.internal.cpk

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.exception.DependencyMetadataException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.security.CodeSigner
import java.util.Base64

class CpkV2DependencyReaderTest {
    private fun hash(base64Hash: String, algorithm: String) =
        SecureHashImpl(algorithm, Base64.getDecoder().decode(base64Hash))

    @Test
    fun `parses dependencies correctly`() {
        val dependenciesDocument = """
        {"formatVersion":"2.0","dependencies":[
            {
                "name": "net.acme.contract",
                "version": "1.0.0",
                "verifyFileHash": {
                    "algorithm": "SHA-256",
                    "fileHash": "qlnYKfLKj931q+pA2BX5N+PlTlcrZbk7XCFq5llOfWs="
                }
            },
            {
                "name": "com.example.helloworld.hello-world-cpk-one",
                "version": "2.0.0",
                "verifySameSignerAsMe": true
            }
        ]}
        """.byteInputStream()

        val codeSigners = mock<List<CodeSigner>>()
        val dependencies = CpkV2DependenciesReader.readDependencies("testCpk.cpk", dependenciesDocument, codeSigners)

        val expectedDependencies = listOf(
            CpkHashDependency(
            "net.acme.contract",
            "1.0.0",
            hash("qlnYKfLKj931q+pA2BX5N+PlTlcrZbk7XCFq5llOfWs=", "SHA-256")),

            CpkSignerDependency(
            "com.example.helloworld.hello-world-cpk-one",
            "2.0.0",
                codeSigners)
        )

        assertEquals(expectedDependencies, dependencies)
    }

    @Test
    fun `parses empty dependencies correctly`() {
        val dependenciesDocument = "{\"formatVersion\":\"2.0\",\"dependencies\":[]}".byteInputStream()

        val dependencies = CpkV2DependenciesReader.readDependencies("testCpk.cpk", dependenciesDocument, mock())

        assertEquals(emptyList<CpkDependency>(), dependencies)
    }

    @Test
    fun `throws if dependency document doesn't conform to schema (missing name)`() {
        // Name is missing
        val dependenciesDocument = """
        {"formatVersion":"2.0","dependencies":[
            {
                "version": "1.0.0",
                "verifyFileHash": {
                    "algorithm": "SHA-256",
                    "fileHash": "qlnYKfLKj931q+pA2BX5N+PlTlcrZbk7XCFq5llOfWs="
                }
            }
        ]}
        """.byteInputStream()

        val exception = assertThrows<DependencyMetadataException> {
            CpkV2DependenciesReader.readDependencies("testCpk.cpk", dependenciesDocument, mock())
        }
        assertNotNull(exception.cause)
        assertNotNull(exception.cause!!.message)
        assertTrue(exception.cause!!.message!!.contains("[\$.dependencies[0].name: is missing but it is required]"))
    }

    @Test
    fun `throws if dependency document doesn't conform to schema (missing version)`() {
        // Version is missing
        val dependenciesDocument = """
        {"formatVersion":"2.0","dependencies":[
            {
                "name": "net.acme.contract",
                "verifyFileHash": {
                    "algorithm": "SHA-256",
                    "fileHash": "qlnYKfLKj931q+pA2BX5N+PlTlcrZbk7XCFq5llOfWs="
                }
            }
        ]}
        """.byteInputStream()

        val exception = assertThrows<DependencyMetadataException> {
            CpkV2DependenciesReader.readDependencies("testCpk.cpk", dependenciesDocument, mock())
        }
        assertNotNull(exception.cause)
        assertNotNull(exception.cause!!.message)
        assertTrue(exception.cause!!.message!!.contains("[\$.dependencies[0].version: is missing but it is required]"))
    }

    @Test
    fun `throws if verifySameSignerAsMe set to false`() {
        val dependenciesDocument = """
        {"formatVersion":"2.0","dependencies":[
            {
                "name": "com.example.helloworld.hello-world-cpk-one",
                "version": "2.0.0",
                "verifySameSignerAsMe": false
            }
        ]}
        """.byteInputStream()

        val exception = assertThrows<DependencyMetadataException> {
            CpkV2DependenciesReader.readDependencies("testCpk.cpk", dependenciesDocument, mock())
        }
        assertNotNull(exception.cause)
        assertNotNull(exception.cause!!.message)
        assertTrue(exception.cause!!.message!!.contains("[\$.dependencies[0].verifySameSignerAsMe: does not" +
                " have a value in the enumeration [true]"))
    }
}