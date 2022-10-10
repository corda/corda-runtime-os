package net.corda.crypto.impl.service

import net.corda.crypto.core.service.CompositeKeyProvider
import net.corda.crypto.impl.cipher.suite.CompositeKeyImpl
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

@Component(service = [CompositeKeyProvider::class])
class CompositeKeyProviderImpl : CompositeKeyProvider {
    fun createFromKeys(keys: List<PublicKey>, threshold: Int?) = CompositeKeyImpl.createFromKeys(keys, threshold)

    override fun create(keys: List<CompositeKeyNodeAndWeight>, threshold: Int?): PublicKey =
        CompositeKeyImpl.create(keys, threshold)

}