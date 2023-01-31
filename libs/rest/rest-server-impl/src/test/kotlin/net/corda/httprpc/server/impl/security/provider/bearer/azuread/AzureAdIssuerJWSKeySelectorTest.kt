package net.corda.httprpc.server.impl.security.provider.bearer.azuread

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.util.Resource
import com.nimbusds.jose.util.ResourceRetriever
import com.nimbusds.jwt.JWTClaimsSet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.URL

class AzureAdIssuerJWSKeySelectorTest {
    private val issuer = "http://issuer.com/"
    private val jwksUrl = "${issuer}jwks"
    private val oidcMetadata = "{\"jwks_uri\": \"${jwksUrl}\", \"issuer\": \"${issuer}\", \"subject_types_supported\":[\"pairwise\"]}"
    private val azureAdIssuers: AzureAdIssuers = mock()
    private val resourceRetriever: ResourceRetriever = mock()
    private val selector = AzureAdIssuerJWSKeySelector(azureAdIssuers, resourceRetriever)

    @BeforeEach
    fun setUp() {
        whenever(azureAdIssuers.valid(issuer)).thenReturn(true)
    }

    @Test
    fun `selectKeys missingHeader shouldThrow`() {
        Assertions.assertThrows(BadJOSEException::class.java) {
            selector.selectKeys(null, null, null)
        }
    }

    @Test
    fun `selectKeys missingIssuer shouldThrow`() {
        Assertions.assertThrows(BadJOSEException::class.java) {
            selector.selectKeys(JWSHeader(JWSAlgorithm.ES256 as JWSAlgorithm), null, null)
        }
    }

    @Test
    fun `selectKeys invalidIssuer shouldThrow`() {
        val claimSet = JWTClaimsSet.Builder().issuer("random").build()
        Assertions.assertThrows(BadJOSEException::class.java) {
            selector.selectKeys(JWSHeader(JWSAlgorithm.ES256 as JWSAlgorithm), claimSet, null)
        }
    }

    @Test
    fun `selectKeys oidcMetadataUnreachable shouldThrow`() {
        whenever(
            resourceRetriever.retrieveResource(
                URL("${issuer.trimEnd('/')}${AzureAdConfiguration.METADATA_PATH}")
            )
        ).thenAnswer { throw IOException() }

        val claimSet = JWTClaimsSet.Builder().issuer(issuer).build()
        Assertions.assertThrows(BadJOSEException::class.java) {
            selector.selectKeys(JWSHeader(JWSAlgorithm.ES256 as JWSAlgorithm), claimSet, null)
        }
    }

    @Test
    fun `selectKeys oidcMetadataInvalid shouldThrow`() {
        whenever(
            resourceRetriever.retrieveResource(
                URL("${issuer.trimEnd('/')}${AzureAdConfiguration.METADATA_PATH}")
            )
        ).thenReturn(Resource("random", "application/json"))

        val claimSet = JWTClaimsSet.Builder().issuer(issuer).build()
        Assertions.assertThrows(BadJOSEException::class.java) {
            selector.selectKeys(JWSHeader(JWSAlgorithm.ES256 as JWSAlgorithm), claimSet, null)
        }
    }

    @Test
    fun `selectKeys jwksUnreachable shouldThrow`() {
        whenever(resourceRetriever.retrieveResource(URL("${issuer.trimEnd('/')}${AzureAdConfiguration.METADATA_PATH}"))).thenReturn(
            Resource(
                oidcMetadata,
                "application/json"
            )
        )
        whenever(resourceRetriever.retrieveResource(URL(jwksUrl))).thenAnswer { throw IOException() }

        val claimSet = JWTClaimsSet.Builder().issuer(issuer).build()
        Assertions.assertThrows(BadJOSEException::class.java) {
            selector.selectKeys(JWSHeader(JWSAlgorithm.ES256 as JWSAlgorithm), claimSet, null)
        }
    }
}
