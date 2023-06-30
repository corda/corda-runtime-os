package net.corda.testing.driver.crypto

import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_RANKING
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(service = [ WrappingRepository::class ], property = [ DRIVER_SERVICE ])
@ServiceRanking(DRIVER_SERVICE_RANKING)
class MemoryWrappingRepository : WrappingRepository {
    private val cache = ConcurrentHashMap<String, WrappingKeyInfo>()

    override fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo {
        cache[alias] = key
        return key
    }

    override fun findKey(alias: String): WrappingKeyInfo? = cache[alias]

    override fun close() {}
}
