package net.corda.crypto.impl

import net.corda.crypto.core.CompositeKeyProvider
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

@Component
class
CompositeKeyProviderImpl : CompositeKeyProvider {
    fun createFromKeys(keys: List<PublicKey>, threshold: Int?) = CompositeKeyImpl.createFromKeys(keys, threshold)

    override fun create(keys: List<CompositeKeyNodeAndWeight>, threshold: Int?): PublicKey =
        CompositeKeyImpl.create(keys, threshold)

}