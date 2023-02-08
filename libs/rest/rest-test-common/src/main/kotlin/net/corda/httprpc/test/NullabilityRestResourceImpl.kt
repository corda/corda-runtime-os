package net.corda.httprpc.test

import net.corda.httprpc.PluggableRestResource

class NullabilityRestResourceImpl : PluggableRestResource<NullabilityRestResource>, NullabilityRestResource {
    override fun postTakesNullableReturnsNullable(someInfo: SomeInfo?): SomeInfo? {
        return SomeInfo("tenantId", 1)
    }

    override fun postTakesInfoReturnsNullable(someInfo: SomeInfo): SomeInfo? {
        return SomeInfo("tenantId", 1)
    }

    override fun postTakesNullableReturnsInfo(someInfo: SomeInfo?): SomeInfo {
        return SomeInfo("tenantId", 1)
    }

    override fun postTakesNullableStringReturnsNullableString(input: String?): String? {
        return null
    }

    override val protocolVersion: Int
        get() = 1

    override val targetInterface: Class<NullabilityRestResource>
        get() = NullabilityRestResource::class.java
}