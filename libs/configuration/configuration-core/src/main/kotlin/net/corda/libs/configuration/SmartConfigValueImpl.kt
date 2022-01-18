package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigMergeable
import com.typesafe.config.ConfigOrigin
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueType
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsLookupService

class SmartConfigValueImpl(
    private val typeSafeConfigValue: ConfigValue,
    private val smartConfigFactory: SmartConfigFactory,
    private val secretsLookupService: SecretsLookupService = maskedSecretsLookupService
) : SmartConfigValue, ConfigValue {
    companion object{
        private val maskedSecretsLookupService = MaskedSecretsLookupService()
    }

    override fun toSafeConfigValue(): SmartConfigValue {
        if(secretsLookupService is MaskedSecretsLookupService)
            return this
        return SmartConfigValueImpl(typeSafeConfigValue, smartConfigFactory, maskedSecretsLookupService)
    }

    override fun equals(other: Any?): Boolean {
        return other is ConfigValue && typeSafeConfigValue == other
    }

    override fun hashCode(): Int {
        return typeSafeConfigValue.hashCode()
    }

    override fun withFallback(other: ConfigMergeable?): ConfigValue? =
        SmartConfigValueImpl(typeSafeConfigValue.withFallback(other), smartConfigFactory, secretsLookupService)

    override fun origin(): ConfigOrigin = typeSafeConfigValue.origin()

    override fun valueType(): ConfigValueType = typeSafeConfigValue.valueType()

    override fun unwrapped(): Any {
        val unwrapped = typeSafeConfigValue.unwrapped()
        if (unwrapped is Map<*,*> && unwrapped.containsKey(SmartConfig.SECRET_KEY)) {
            @Suppress("UNCHECKED_CAST")
            return secretsLookupService.getValue(ConfigFactory.parseMap(unwrapped as Map<String,Any>))
        }
        return unwrapped
    }

    override fun render(): String = typeSafeConfigValue.render()

    override fun render(options: ConfigRenderOptions?): String = typeSafeConfigValue.render(options)

    override fun atPath(path: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfigValue.atPath(path), smartConfigFactory, secretsLookupService)

    override fun atKey(key: String?): SmartConfig =
        SmartConfigImpl(typeSafeConfigValue.atKey(key), smartConfigFactory, secretsLookupService)

    override fun withOrigin(origin: ConfigOrigin?): SmartConfigValue? =
        SmartConfigValueImpl(typeSafeConfigValue.withOrigin(origin), smartConfigFactory, secretsLookupService)
}