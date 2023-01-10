package net.corda.testing.driver.crypto.impl

import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import org.osgi.service.component.annotations.Component

@Component(property = [ DRIVER_SERVICE ])
class MemoryWrappingRepository : WrappingRepository {
    private val cache = ConcurrentHashMap<String, WrappingKeyInfo>()

    override fun saveKey(alias: String, key: WrappingKeyInfo) {
        cache[alias] = key
    }

    override fun findKey(alias: String): WrappingKeyInfo? = cache[alias]

    override fun close() {}
}
