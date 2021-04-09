package api.samples.config

import api.samples.config.ConfigCache
import api.samples.config.ConfigService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference


/**
 * Use DI to inject a cache/source of truth to get values
 */
@Component
class ConfigServiceImpl @Activate constructor(
    @Reference(service = ConfigCache::class)
    private val configCache: ConfigCache
): ConfigService {

    override fun getSomeValue () : String {
        return configCache.getSomeValue()
    }

    override fun getSomeOtherValue () : String {
        return configCache.getSomeOtherValue()
    }

    override fun getSomeOtherValues () : List<String> {
        return configCache.getSomeOtherValues()
    }

    override fun getSomeMoreValues () : List<Long> {
        return configCache.getSomeMoreValues()
    }
}