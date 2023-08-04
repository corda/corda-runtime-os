package net.corda.testing.driver.processor.crypto

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.testing.driver.sandbox.DRIVER_SERVICE
import net.corda.testing.driver.sandbox.DRIVER_SERVICE_RANKING
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(service = [ WrappingRepository::class ], property = [ DRIVER_SERVICE ])
@ServiceRanking(DRIVER_SERVICE_RANKING)
class MemoryWrappingRepository : WrappingRepository {
    private val cache = ConcurrentHashMap<String, WrappingKeyInfo>()
    private val idCache = ConcurrentHashMap<String, UUID>()

    override fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo {
        cache[alias] = key
        return key
    }

    override fun saveKeyWithId(alias: String, key: WrappingKeyInfo, id: UUID?): WrappingKeyInfo {
        idCache[alias] = (id ?: UUID.randomUUID())
        return saveKey(alias, key)
    }

    override fun findKey(alias: String): WrappingKeyInfo? = cache[alias]

    override fun findKeyAndId(alias: String): Pair<UUID, WrappingKeyInfo>? {
        return idCache[alias]?.let { id ->
            cache[alias]?.let { key ->
                Pair(id, key)
            }
        }
    }

    override fun close() {}
}
