package net.corda.crypto.tck.impl

import net.corda.crypto.cipher.suite.CryptoServiceProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption

@Component(service = [CryptoServiceProviderMap::class])
class CryptoServiceProviderMap @Activate constructor (
    @Reference(
        service = CryptoServiceProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>
) {
    private val map = cryptoServiceProviders.associateBy { it.name }

    @Suppress("UNCHECKED_CAST")
    fun get(name: String): CryptoServiceProvider<Any> = map.getValue(name) as CryptoServiceProvider<Any>

    fun all() = map.values.toList()
}