package net.corda.libs.configuration

import com.typesafe.config.Config
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType

@Suppress("TooManyFunctions")
class SmartConfigObjectImpl(
    private val typeSafeConfigObject: ConfigObject,
    private val secretsLookupService: SecretsLookupService = noopSecretsLookupService
) : SmartConfigObject {
    companion object{
        private val noopSecretsLookupService = NoopSecretsLookupService()
    }

    override fun equals(other: Any?): Boolean {
        return other is SmartConfigObject && typeSafeConfigObject == other
    }

    override fun hashCode(): Int {
        return typeSafeConfigObject.hashCode()
    }

    override fun toSafeConfig(): SmartConfigObject {
        if(secretsLookupService is NoopSecretsLookupService)
            return this
        return SmartConfigObjectImpl(typeSafeConfigObject, noopSecretsLookupService)
    }

    // NOTE: render will always use Noop Secrets Lookup
    override fun render(): String {
        if(secretsLookupService is NoopSecretsLookupService)
            return typeSafeConfigObject.render()
        return toSafeConfig().render()
    }

    override fun render(options: ConfigRenderOptions?): String {
        if(secretsLookupService is NoopSecretsLookupService)
            return typeSafeConfigObject.render(options)
        return toSafeConfig().render()
    }

    override fun toConfig(): Config? =
        SmartConfigImpl(typeSafeConfigObject.toConfig(), secretsLookupService)

    override fun withOnlyKey(key: String?): ConfigObject? =
        SmartConfigObjectImpl(typeSafeConfigObject.withOnlyKey(key), secretsLookupService)

    override fun withoutKey(key: String?): ConfigObject? =
        SmartConfigObjectImpl(typeSafeConfigObject.withoutKey(key), secretsLookupService)

    override fun withValue(key: String?, value: ConfigValue?): ConfigObject? =
        SmartConfigObjectImpl(typeSafeConfigObject.withValue(key, value), secretsLookupService)

    override val entries: MutableSet<MutableMap.MutableEntry<String, ConfigValue>>
        get() = typeSafeConfigObject.entries
    override val keys: MutableSet<String>
        get() = typeSafeConfigObject.keys
    override val values: MutableCollection<ConfigValue>
        get() = typeSafeConfigObject.values.map { SmartConfigValueImpl(it) }.toMutableList()
    override val size: Int
        get() = typeSafeConfigObject.size

    override fun unwrapped(): Map<String?, Any?>? = typeSafeConfigObject.unwrapped()

    override fun atPath(path: String?): Config = SmartConfigImpl(typeSafeConfigObject.atPath(path))

    override fun atKey(key: String?): Config = SmartConfigImpl(typeSafeConfigObject.atPath(key))

    override fun withFallback(other: ConfigMergeable?): ConfigObject? =
        SmartConfigObjectImpl(typeSafeConfigObject.withFallback(other), secretsLookupService)

    override fun origin(): ConfigOrigin {
        TODO("Not yet implemented")
    }

    override fun valueType(): ConfigValueType = typeSafeConfigObject.valueType()

    override fun withOrigin(origin: ConfigOrigin?): ConfigObject? =
        SmartConfigObjectImpl(typeSafeConfigObject.withOrigin(origin), secretsLookupService)

    override fun clear() = typeSafeConfigObject.clear()

    override fun put(key: String, value: ConfigValue): ConfigValue? {
        val v = typeSafeConfigObject.put(key, value)
        if (null == v)
            return v
        return SmartConfigValueImpl(v, secretsLookupService)
    }

    override fun putAll(from: Map<out String, ConfigValue>) =
        typeSafeConfigObject.putAll((from))

    override fun remove(key: String?): ConfigValue?{
        val v = typeSafeConfigObject.remove(key)
        if (null == v)
            return v
        return SmartConfigValueImpl(v, secretsLookupService)
    }

    override fun containsKey(key: String?): Boolean = typeSafeConfigObject.containsKey(key)

    override fun containsValue(value: ConfigValue?): Boolean = typeSafeConfigObject.containsValue(value)

    override fun get(key: String?): ConfigValue? {
        val v = typeSafeConfigObject.get(key)
        if (null == v)
            return v
        return SmartConfigValueImpl(v, secretsLookupService)
    }

    override fun isEmpty(): Boolean = typeSafeConfigObject.isEmpty()
}