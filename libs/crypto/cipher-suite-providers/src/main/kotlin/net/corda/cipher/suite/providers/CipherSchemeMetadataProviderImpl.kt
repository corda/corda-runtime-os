package net.corda.cipher.suite.providers

import net.corda.crypto.impl.CipherSchemeMetadataFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import org.osgi.service.component.annotations.Component

@Component(service = [CipherSchemeMetadataProvider::class])
class CipherSchemeMetadataProviderImpl : CipherSchemeMetadataProvider {
    companion object {
        const val SERVICE_NAME = "default"
    }

    private val factory: CipherSchemeMetadataFactory by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CipherSchemeMetadataFactory()
    }

    override val name: String = SERVICE_NAME

    override fun getInstance(): CipherSchemeMetadata = factory.getInstance()
}