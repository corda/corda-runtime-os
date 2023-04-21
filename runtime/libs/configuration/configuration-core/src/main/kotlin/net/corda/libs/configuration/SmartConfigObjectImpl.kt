package net.corda.libs.configuration

import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsLookupService

@Suppress("TooManyFunctions")
class SmartConfigObjectImpl(
    private val typeSafeConfigObject: ConfigObject,
    private val smartConfigFactory: SmartConfigFactory,
    private val secretsLookupService: SecretsLookupService = maskedSecretsLookupService
) : SmartConfigObject {
    companion object{
        private val maskedSecretsLookupService = MaskedSecretsLookupService()
    }

    override fun equals(other: Any?): Boolean {
        return other is SmartConfigObject && typeSafeConfigObject == other
    }

    override fun hashCode(): Int {
        return typeSafeConfigObject.hashCode()
    }

    override fun toSafeConfig(): SmartConfigObject {
        if(secretsLookupService is MaskedSecretsLookupService)
            return this
        return SmartConfigObjectImpl(typeSafeConfigObject, smartConfigFactory, maskedSecretsLookupService)
    }

    // NOTE: render will always use Noop Secrets Lookup
    override fun render(): String {
        if(secretsLookupService is MaskedSecretsLookupService)
            return typeSafeConfigObject.render()
        return toSafeConfig().render()
    }

    override fun render(options: ConfigRenderOptions?): String {
        if(secretsLookupService is MaskedSecretsLookupService)
            return typeSafeConfigObject.render(options)
        return toSafeConfig().render(options)
    }

    override fun toConfig(): SmartConfig =
        SmartConfigImpl(typeSafeConfigObject.toConfig(), smartConfigFactory, secretsLookupService)

    override fun withOnlyKey(key: String?): SmartConfigObject =
        SmartConfigObjectImpl(typeSafeConfigObject.withOnlyKey(key), smartConfigFactory, secretsLookupService)

    override fun withoutKey(key: String?): SmartConfigObject =
        SmartConfigObjectImpl(typeSafeConfigObject.withoutKey(key), smartConfigFactory, secretsLookupService)

    override fun withValue(key: String?, value: ConfigValue?): SmartConfigObject =
        SmartConfigObjectImpl(typeSafeConfigObject.withValue(key, value), smartConfigFactory, secretsLookupService)

    override val entries: MutableSet<MutableMap.MutableEntry<String, ConfigValue>>
        get() = typeSafeConfigObject.entries
    override val keys: MutableSet<String>
        get() = typeSafeConfigObject.keys
    override val values: MutableCollection<ConfigValue>
        get() = typeSafeConfigObject.values.map { SmartConfigValueImpl(it, smartConfigFactory) }.toMutableList()
    override val size: Int
        get() = typeSafeConfigObject.size

    override fun unwrapped(): Map<String?, Any?>? = typeSafeConfigObject.unwrapped()

    override fun atPath(path: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfigObject.atPath(path), smartConfigFactory, secretsLookupService)

    override fun atKey(key: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfigObject.atPath(key), smartConfigFactory, secretsLookupService)

    override fun withFallback(other: ConfigMergeable?): SmartConfigObject =
        SmartConfigObjectImpl(typeSafeConfigObject.withFallback(other), smartConfigFactory, secretsLookupService)

    override fun origin(): ConfigOrigin {
        return typeSafeConfigObject.origin()
    }

    override fun valueType(): ConfigValueType = typeSafeConfigObject.valueType()

    override fun withOrigin(origin: ConfigOrigin?): SmartConfigObject =
        SmartConfigObjectImpl(typeSafeConfigObject.withOrigin(origin), smartConfigFactory, secretsLookupService)

    override fun clear() = typeSafeConfigObject.clear()

    override fun put(key: String, value: ConfigValue): SmartConfigValue? {
        val v = typeSafeConfigObject.put(key, value)
        if (null == v)
            return v
        return SmartConfigValueImpl(v, smartConfigFactory, secretsLookupService)
    }

    override fun putAll(from: Map<out String, ConfigValue>) =
        typeSafeConfigObject.putAll((from))

    override fun remove(key: String?): SmartConfigValue?{
        val v = typeSafeConfigObject.remove(key)
        if (null == v)
            return v
        return SmartConfigValueImpl(v, smartConfigFactory, secretsLookupService)
    }

    override fun containsKey(key: String?): Boolean = typeSafeConfigObject.containsKey(key)

    override fun containsValue(value: ConfigValue?): Boolean = typeSafeConfigObject.containsValue(value)

    override fun get(key: String?): SmartConfigValue? {
        val v = typeSafeConfigObject.get(key)
        if (null == v)
            return v
        return SmartConfigValueImpl(v, smartConfigFactory, secretsLookupService)
    }

    override fun isEmpty(): Boolean = typeSafeConfigObject.isEmpty()
}