package net.corda.crypto.impl

import net.corda.crypto.core.CompositeKeyProvider
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

@Component(service = [ CompositeKeyProvider::class, SingletonSerializeAsToken::class ])
class
CompositeKeyProviderImpl : CompositeKeyProvider, SingletonSerializeAsToken {
    fun createFromKeys(keys: List<PublicKey>, threshold: Int?) = CompositeKeyImpl.createFromKeys(keys, threshold)

    override fun create(keys: List<CompositeKeyNodeAndWeight>, threshold: Int?): PublicKey =
        CompositeKeyImpl.create(keys, threshold)
}
