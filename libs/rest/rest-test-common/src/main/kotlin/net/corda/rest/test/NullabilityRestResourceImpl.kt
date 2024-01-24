package net.corda.rest.test

import net.corda.rest.PluggableRestResource

class NullabilityRestResourceImpl : PluggableRestResource<NullabilityRestResource>, NullabilityRestResource {
    override fun postTakesNullableReturnsNullable(optionalSomeInfo: SomeInfo?): SomeInfo {
        return SomeInfo("tenantId", 1)
    }

    override fun postTakesInfoReturnsNullable(requiredSomeInfo: SomeInfo): SomeInfo {
        return SomeInfo("tenantId", 1)
    }

    override fun postTakesNullableReturnsInfo(optionalSomeInfo: SomeInfo?): SomeInfo {
        return SomeInfo("tenantId", 1)
    }

    override fun postTakesNullableStringReturnsNullableString(optionalString: String?): String? {
        return null
    }

    override fun postTakesRequiredStringReturnsNullableString(requiredString: String): String? {
        return null
    }

    override val protocolVersion: Int
        get() = 1

    override val targetInterface: Class<NullabilityRestResource>
        get() = NullabilityRestResource::class.java
}
