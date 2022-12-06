package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.crypto.core.OID_COMPOSITE_SIGNATURE_IDENTIFIER
import java.security.Provider
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

// Install various Corda specific security services into Java Security, including Java
//
class CordaSecurityProvider(
    keyEncoder: KeyEncodingService
) : Provider(PROVIDER_NAME, "0.1", "$PROVIDER_NAME security provider wrapper") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    init {
        putService(
            CompositeKeyFactoryService(this, keyEncoder)
        )
        putService(
            createCompositeSignatureService(this)
        )
        putService(
            CordaSecureRandomService(this)
        )
    }

    override fun getService(type: String, algorithm: String): Service? = serviceFactory(type, algorithm)

    private val serviceFactory: (String, String) -> Service? = try {
        makeCachingFactory()
    } catch (e: Throwable) {
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

    private fun createCompositeSignatureService(provider: Provider): Service =
        Service(
            provider,
            "Signature",
            CompositeSignature.SIGNATURE_ALGORITHM,
            CompositeSignature::class.java.name,
            listOf(
                OID_COMPOSITE_SIGNATURE_IDENTIFIER.toString(),
                "OID.$OID_COMPOSITE_SIGNATURE_IDENTIFIER"
            ),
            null
        )

    private class CompositeKeyFactoryService(
        provider: Provider,
        private val keyEncoder: KeyEncodingService
    ) : Service(
        provider,
        "KeyFactory",
        CompositeKeyImpl.KEY_ALGORITHM,
        CompositeKeyFactory::class.java.name,
        listOf(
            OID_COMPOSITE_KEY_IDENTIFIER.toString(),
            "OID.$OID_COMPOSITE_KEY_IDENTIFIER"
        ),
        null
    ) {
        override fun newInstance(constructorParameter: Any?): Any = CompositeKeyFactory(keyEncoder)
    }
}

