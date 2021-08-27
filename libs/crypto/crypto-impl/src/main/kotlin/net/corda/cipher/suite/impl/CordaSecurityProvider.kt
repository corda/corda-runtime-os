package net.corda.cipher.suite.impl

import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.OID_COMPOSITE_SIGNATURE_IDENTIFIER
import java.security.Provider
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class CordaSecurityProvider(
    keyEncoderProvider: () -> KeyEncodingService
) : Provider(PROVIDER_NAME, "0.1", "$PROVIDER_NAME security provider wrapper") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    init {
        putService(CompositeKeyFactoryService(this, keyEncoderProvider))
        putService(
            createService(
                "Signature", CompositeSignature.SIGNATURE_ALGORITHM, CompositeSignature::class.java.name,
                listOf(OID_COMPOSITE_SIGNATURE_IDENTIFIER.toString(), "OID.$OID_COMPOSITE_SIGNATURE_IDENTIFIER")
            )
        )
        putPlatformSecureRandomService()
    }

    private fun putPlatformSecureRandomService() {
        putService(CordaSecureRandomService(this))
    }

    override fun getService(type: String, algorithm: String): Service? = serviceFactory(type, algorithm)

    @Suppress("TooGenericExceptionCaught")
    private val serviceFactory: (String, String) -> Service? = try {
        makeCachingFactory()
    } catch (e: Exception) {
        makeFactory()
    }

    private fun superGetService(type: String, algorithm: String): Service? = super.getService(type, algorithm)

    private fun makeCachingFactory(): Function2<String, String, Service?> {
        return object : Function2<String, String, Service?> {
            private val services = ConcurrentHashMap<Pair<String, String>, Optional<Service>>()

            override fun invoke(type: String, algorithm: String): Service? {
                return services.getOrPut(Pair(type, algorithm)) {
                    Optional.ofNullable(superGetService(type, algorithm))
                }.orElse(null)
            }
        }
    }

    private fun makeFactory(): Function2<String, String, Service?> {
        return object : Function2<String, String, Service?> {
            override fun invoke(type: String, algorithm: String): Service? {
                return superGetService(type, algorithm)
            }
        }
    }

    private fun createService(type: String, algorithm: String, className: String, aliases: List<String>? = null): Service {
        return Service(this, type, algorithm, className, aliases, null)
    }

    private class CompositeKeyFactoryService(
        provider: Provider,
        private val keyEncoderProvider: () -> KeyEncodingService
    ) : Service(
        provider,
        "KeyFactory",
        CompositeKey.KEY_ALGORITHM,
        CompositeKeyFactory::class.java.name,
        listOf(
            OID_COMPOSITE_KEY_IDENTIFIER.toString(),
            "OID.$OID_COMPOSITE_KEY_IDENTIFIER"
        ),
        null
    ) {
        override fun newInstance(constructorParameter: Any?): Any = CompositeKeyFactory(keyEncoderProvider())
    }
}

