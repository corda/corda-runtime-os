package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryGroupPolicy
import net.corda.data.membership.db.response.query.GroupPolicyQueryResponse
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.virtualnode.toCorda

internal class QueryGroupPolicyHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryGroupPolicy, GroupPolicyQueryResponse>(persistenceHandlerServices) {
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: QueryGroupPolicy
    ): GroupPolicyQueryResponse {
        logger.info("Searching for group policy for identity ${context.holdingIdentity}.")
//        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
//            val result = em.createQuery(
//                "SELECT * FROM ${GroupPolicyEntity::class.java.simpleName} ORDER BY version DESC LIMIT 1",
//                GroupPolicyEntity::class.java
//            ).resultList
//            if(result.isEmpty()) {
//                GroupPolicyQueryResponse(KeyValuePairList(emptyList<KeyValuePair>()))
//            } else {
//                GroupPolicyQueryResponse(keyValuePairListDeserializer.deserialize(result.first().properties))
//            }
//        }
        return GroupPolicyQueryResponse(
            KeyValuePairList(
                listOf(
                    KeyValuePair(
                        "corda.group.protocol.registration",
                        "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService"
                    ),
                    KeyValuePair(
                        "corda.group.protocol.synchronisation",
                        "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl"
                    ),
                    KeyValuePair("corda.group.protocol.p2p.mode", "AUTHENTICATED_ENCRYPTION"),
                    KeyValuePair("corda.group.key.session.policy", "Combined"),
                    KeyValuePair("corda.group.pki.session", "Standard"),
                    KeyValuePair("corda.group.pki.tls", "Standard"),
                    KeyValuePair("corda.group.tls.version", "1.3"),
                    KeyValuePair("corda.endpoints.0.connectionURL", "localhost:1080"),
                    KeyValuePair("corda.endpoints.0.protocolVersion", "1"),
                    KeyValuePair(
                        "corda.group.truststore.session.0",
                        "-----BEGIN CERTIFICATE-----\nMIIFHTCCBAWgAwIBAgISA3oME5BKdlKjaDetWNzchMLCMA0GCSqGSIb3DQEBCwUA\nMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\nEwJSMzAeFw0yMjAzMTIxNTA5NTBaFw0yMjA2MTAxNTA5NDlaMBExDzANBgNVBAMT\nBnIzLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMOna/+\nr0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmiVzKn\n6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoCDnKg\nzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEHsxMK\n+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lFZ2ro\nWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gzMHdf\nNwDftNqTuMkCAwEAAaOCAkwwggJIMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAU\nBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUtd2w\n7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+vnYsU\nwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5sZW5j\nci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wHQYDVR0R\nBBYwFIIGcjMuY29tggp3d3cucjMuY29tMEwGA1UdIARFMEMwCAYGZ4EMAQIBMDcG\nCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5\ncHQub3JnMIIBAwYKKwYBBAHWeQIEAgSB9ASB8QDvAHYAQcjKsd8iRkoQxqE6CUKH\nXk4xixsD6+tLx2jwkGKWBvYAAAF/fuSxLwAABAMARzBFAiAIRlYnASUhCU7HHhnL\nvHeJ3wbSaJjlKyb+XA+uN1Q/NQIhALHf5oJtIV9rR8+E609k0tR8xyGH4B9SBz7g\nzSdv+qHlAHUARqVV63X6kSAwtaKJafTzfREsQXS+/Um4havy/HD+bUcAAAF/fuSx\ncAAABAMARjBEAiAKFkMCFKOo/b4FZ1BEX5czc52yavX7S+1d7BEcGfV0DgIgCOvQ\nC2DzbQTmC16xsYuhv8X+uylxRiD1M5f8dbkcwjwwDQYJKoZIhvcNAQELBQADggEB\nAJ0Jm/xPtGGY0SbSoNKHg7rd6dq+p21rDQS06QcCI4PxpyZy1ajTLm0Cru5akm95\nKcGooxTOysMUZ8o/91chFM8yyFOdVgGoeMAEm0qZz6Mbj3FOgwdeZtP9QQZGXpDy\nf/WDuYZnZPkTFAZwDE5mvGkF0gfCfEp/TV9INJ82OsvAIR6g23wFGPn0StgUDUfC\nfvjzohb01tAp1/2ds2w4sdyl7bB2TMOyfwgllvxBfDSZloL6df4E8wi7Y9Lobca7\nwKd75N1i8bNI4OAsACrs3nB8DIi4R9PKRITFtpsfFOdqLjnFWMKFL4sbcc3M4yea\nWHG8M+mH64sx3rTHzZmF65s=\n-----END CERTIFICATE-----"
                    ),
                    KeyValuePair(
                        "corda.group.truststore.tls.0",
                        "-----BEGIN CERTIFICATE-----\nMIIFHTCCBAWgAwIBAgISA3oME5BKdlKjaDetWNzchMLCMA0GCSqGSIb3DQEBCwUA\nMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\nEwJSMzAeFw0yMjAzMTIxNTA5NTBaFw0yMjA2MTAxNTA5NDlaMBExDzANBgNVBAMT\nBnIzLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMOna/+\nr0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmiVzKn\n6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoCDnKg\nzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEHsxMK\n+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lFZ2ro\nWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gzMHdf\nNwDftNqTuMkCAwEAAaOCAkwwggJIMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAU\nBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUtd2w\n7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+vnYsU\nwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5sZW5j\nci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wHQYDVR0R\nBBYwFIIGcjMuY29tggp3d3cucjMuY29tMEwGA1UdIARFMEMwCAYGZ4EMAQIBMDcG\nCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5\ncHQub3JnMIIBAwYKKwYBBAHWeQIEAgSB9ASB8QDvAHYAQcjKsd8iRkoQxqE6CUKH\nXk4xixsD6+tLx2jwkGKWBvYAAAF/fuSxLwAABAMARzBFAiAIRlYnASUhCU7HHhnL\nvHeJ3wbSaJjlKyb+XA+uN1Q/NQIhALHf5oJtIV9rR8+E609k0tR8xyGH4B9SBz7g\nzSdv+qHlAHUARqVV63X6kSAwtaKJafTzfREsQXS+/Um4havy/HD+bUcAAAF/fuSx\ncAAABAMARjBEAiAKFkMCFKOo/b4FZ1BEX5czc52yavX7S+1d7BEcGfV0DgIgCOvQ\nC2DzbQTmC16xsYuhv8X+uylxRiD1M5f8dbkcwjwwDQYJKoZIhvcNAQELBQADggEB\nAJ0Jm/xPtGGY0SbSoNKHg7rd6dq+p21rDQS06QcCI4PxpyZy1ajTLm0Cru5akm95\nKcGooxTOysMUZ8o/91chFM8yyFOdVgGoeMAEm0qZz6Mbj3FOgwdeZtP9QQZGXpDy\nf/WDuYZnZPkTFAZwDE5mvGkF0gfCfEp/TV9INJ82OsvAIR6g23wFGPn0StgUDUfC\nfvjzohb01tAp1/2ds2w4sdyl7bB2TMOyfwgllvxBfDSZloL6df4E8wi7Y9Lobca7\nwKd75N1i8bNI4OAsACrs3nB8DIi4R9PKRITFtpsfFOdqLjnFWMKFL4sbcc3M4yea\nWHG8M+mH64sx3rTHzZmF65s=\n-----END CERTIFICATE-----"
                    )
                )
            )
        )
    }

}