package net.corda.cipher.suite.providers

import net.corda.crypto.impl.CipherSchemeMetadataImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import org.osgi.service.component.annotations.Component

@Component(service = [CipherSchemeMetadataProvider::class])
class CipherSchemeMetadataProviderImpl : CipherSchemeMetadataProvider {
    companion object {
        const val SERVICE_NAME = "default"
    }

    private val schemeMetadata: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CipherSchemeMetadataImpl()
    }

    override val name: String = SERVICE_NAME

    override fun getInstance(): CipherSchemeMetadata = schemeMetadata
}