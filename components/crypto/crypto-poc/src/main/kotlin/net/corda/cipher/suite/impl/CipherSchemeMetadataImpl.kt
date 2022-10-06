package net.corda.cipher.suite.impl

import net.corda.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.cipher.suite.CipherSchemeMetadata
import net.corda.cipher.suite.KeyEncodingService
import org.osgi.service.component.annotations.Component

@Component(
    service = [
        CipherSchemeMetadata::class,
        KeyEncodingService::class,
        AlgorithmParameterSpecEncodingService::class
    ]
)class CipherSchemeMetadataImpl : CipherSchemeMetadata {
}