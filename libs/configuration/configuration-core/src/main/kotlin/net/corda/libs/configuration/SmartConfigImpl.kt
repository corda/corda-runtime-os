package net.corda.libs.configuration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigMemorySize
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.ConfigValue
import java.time.Duration
import java.time.Period
import java.time.temporal.TemporalAmount
import java.util.concurrent.TimeUnit

/**
 * Default implementation of [SmartConfig] that wraps existing [Config]
 *
 * @property typeSafeConfig
 * @property secretsLookupService to use, defaults to Noop Service that will never reveal a secret
 * @constructor Create empty Smart config impl
 */
@Suppress("TooManyFunctions")
class SmartConfigImpl(
    val typeSafeConfig: Config,
    private val secretsLookupService: SecretsLookupService = maskedSecretsLookupService
) : SmartConfig {
    companion object{
        fun empty(): SmartConfig = SmartConfigImpl(ConfigFactory.empty())

        private val maskedSecretsLookupService = MaskedSecretsLookupService()
    }

    override fun equals(other: Any?): Boolean {
        if(other is SmartConfigImpl)
            return typeSafeConfig == other.typeSafeConfig
        if(other is Config)
            return typeSafeConfig == other
        return false
    }

    override fun hashCode(): Int {
        return typeSafeConfig.hashCode()
    }

    override fun isSecret(path: String): Boolean =
        typeSafeConfig.hasPath("$path.$SECRETS_INDICATOR")

    override fun convert(config: Config): SmartConfig {
        return SmartConfigImpl(config, secretsLookupService)
    }

    override fun toSafeConfig(): SmartConfig {
        if(secretsLookupService is MaskedSecretsLookupService)
            return this
        return SmartConfigImpl(typeSafeConfig, maskedSecretsLookupService)
    }

    override fun withFallback(other: ConfigMergeable?): SmartConfig {
        val o = if (other is SmartConfigImpl) { other.typeSafeConfig } else { other }
        return SmartConfigImpl(typeSafeConfig.withFallback(o), secretsLookupService)
    }

    override fun root(): SmartConfigObject =
        SmartConfigObjectImpl(typeSafeConfig.root(), secretsLookupService)

    override fun origin(): ConfigOrigin = typeSafeConfig.origin()

    override fun resolve(): SmartConfig = SmartConfigImpl(typeSafeConfig.resolve(), secretsLookupService)

    override fun resolve(options: ConfigResolveOptions?): SmartConfig =
        SmartConfigImpl(typeSafeConfig.resolve(options), secretsLookupService)

    override fun isResolved(): Boolean = typeSafeConfig.isResolved

    override fun resolveWith(source: Config): SmartConfig =
        SmartConfigImpl(typeSafeConfig.resolveWith(source), secretsLookupService)

    override fun resolveWith(source: Config, options: ConfigResolveOptions?): SmartConfig =
        SmartConfigImpl(
            typeSafeConfig.resolveWith(SmartConfigImpl(source, secretsLookupService), options),
            secretsLookupService)

    override fun checkValid(reference: Config, vararg restrictToPaths: String) {
        // checkvalid casts to SimpleConfig, so validating the underlying config here should be ok
        typeSafeConfig.checkValid(reference, *restrictToPaths)
    }

    override fun hasPath(path: String?): Boolean = typeSafeConfig.hasPath(path)

    override fun hasPathOrNull(path: String?): Boolean = typeSafeConfig.hasPathOrNull(path)

    override fun isEmpty(): Boolean = typeSafeConfig.isEmpty

    override fun entrySet(): MutableSet<MutableMap.MutableEntry<String, SmartConfigValue>> {
        val map = mutableMapOf<String, SmartConfigValue>()
        typeSafeConfig.entrySet().forEach { map[it.key] = SmartConfigValueImpl(it.value, secretsLookupService) }
        return map.entries.toMutableSet()
    }

    override fun getIsNull(path: String?): Boolean = typeSafeConfig.getIsNull(path)

    override fun getBoolean(path: String?): Boolean = typeSafeConfig.getBoolean(path)

    override fun getNumber(path: String?): Number = typeSafeConfig.getNumber(path)

    override fun getInt(path: String?): Int = typeSafeConfig.getInt(path)

    override fun getLong(path: String?): Long = typeSafeConfig.getLong(path)

    override fun getDouble(path: String?): Double = typeSafeConfig.getDouble(path)

    override fun getString(path: String): String {
        if (isSecret(path))
            return secretsLookupService.getValue(typeSafeConfig.getValue(path))
        return typeSafeConfig.getString(path)
    }

    override fun <T : Enum<T>?> getEnum(enumClass: Class<T>?, path: String?): T =
        typeSafeConfig.getEnum(enumClass, path)

    override fun getObject(path: String?): SmartConfigObject =
        SmartConfigObjectImpl(typeSafeConfig.getObject(path), secretsLookupService)

    override fun getConfig(path: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfig.getConfig(path), secretsLookupService)

    override fun getAnyRef(path: String): Any {
        if (isSecret(path))
            return secretsLookupService.getValue(typeSafeConfig.getValue(path))
        return typeSafeConfig.getAnyRef(path)
    }

    override fun getValue(path: String?): SmartConfigValue =
        SmartConfigValueImpl(typeSafeConfig.getValue(path), secretsLookupService)

    override fun getBytes(path: String?): Long = typeSafeConfig.getBytes(path)

    override fun getMemorySize(path: String?): ConfigMemorySize = typeSafeConfig.getMemorySize(path)

    override fun getMilliseconds(path: String?): Long {
        throw UnsupportedOperationException("Deprecated")
    }

    override fun getNanoseconds(path: String?): Long {
        throw UnsupportedOperationException("Deprecated")
    }

    override fun getDuration(path: String?, unit: TimeUnit?): Long =
        typeSafeConfig.getDuration(path, unit)

    override fun getDuration(path: String?): Duration = typeSafeConfig.getDuration(path)

    override fun getPeriod(path: String?): Period = typeSafeConfig.getPeriod(path)

    override fun getTemporal(path: String?): TemporalAmount = typeSafeConfig.getTemporal(path)

    override fun getList(path: String?): ConfigList = typeSafeConfig.getList(path)

    override fun getBooleanList(path: String?): MutableList<Boolean> =
        typeSafeConfig.getBooleanList(path)

    override fun getNumberList(path: String?): MutableList<Number> =
        typeSafeConfig.getNumberList(path)

    override fun getIntList(path: String?): MutableList<Int> = typeSafeConfig.getIntList(path)

    override fun getLongList(path: String?): MutableList<Long> = typeSafeConfig.getLongList(path)

    override fun getDoubleList(path: String?): MutableList<Double> = typeSafeConfig.getDoubleList(path)

    override fun getStringList(path: String?): MutableList<String> = typeSafeConfig.getStringList(path)

    override fun <T : Enum<T>?> getEnumList(enumClass: Class<T>?, path: String?): MutableList<T> =
        typeSafeConfig.getEnumList(enumClass, path)

    override fun getObjectList(path: String?): MutableList<out ConfigObject> = typeSafeConfig.getObjectList(path)

    override fun getConfigList(path: String?): MutableList<out Config> = typeSafeConfig.getConfigList(path)

    override fun getAnyRefList(path: String?): MutableList<out Any> = typeSafeConfig.getAnyRefList(path)

    override fun getBytesList(path: String?): MutableList<Long> = typeSafeConfig.getBytesList(path)

    override fun getMemorySizeList(path: String?): MutableList<ConfigMemorySize> =
        typeSafeConfig.getMemorySizeList(path)

    override fun getMillisecondsList(path: String?): MutableList<Long> {
        throw UnsupportedOperationException("Deprecated")
    }

    override fun getNanosecondsList(path: String?): MutableList<Long> {
        throw UnsupportedOperationException("Deprecated")
    }

    override fun getDurationList(path: String?, unit: TimeUnit?): MutableList<Long> =
        typeSafeConfig.getDurationList(path, unit)

    override fun getDurationList(path: String?): MutableList<Duration> = typeSafeConfig.getDurationList(path)

    override fun withOnlyPath(path: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfig.withOnlyPath(path), secretsLookupService)

    override fun withoutPath(path: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfig.withoutPath(path), secretsLookupService)

    override fun atPath(path: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfig.atPath(path), secretsLookupService)

    override fun atKey(key: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfig.atKey(key), secretsLookupService)

    override fun withValue(path: String?, value: ConfigValue?): SmartConfig =
        SmartConfigImpl(typeSafeConfig.withValue(path, value), secretsLookupService)
}

