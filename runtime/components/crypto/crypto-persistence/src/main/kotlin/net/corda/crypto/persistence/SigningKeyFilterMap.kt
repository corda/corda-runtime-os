package net.corda.crypto.persistence

import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.EXTERNAL_ID_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.v5.base.types.LayeredPropertyMap
import java.time.Instant

interface SigningKeyFilterMap : LayeredPropertyMap

class SigningKeyFilterMapImpl(
    private val map: LayeredPropertyMap
) : LayeredPropertyMap by map, SigningKeyFilterMap {
    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is SigningKeyFilterMapImpl) return false
        return map == other.map
    }
}

val SigningKeyFilterMap.category: String? get() =
    parseOrNull(CATEGORY_FILTER, String::class.java)

val SigningKeyFilterMap.schemeCodeName: String? get() =
    parseOrNull(SCHEME_CODE_NAME_FILTER, String::class.java)

val SigningKeyFilterMap.alias: String? get() =
    parseOrNull(ALIAS_FILTER, String::class.java)

val SigningKeyFilterMap.masterKeyAlias: String? get() =
    parseOrNull(MASTER_KEY_ALIAS_FILTER, String::class.java)

val SigningKeyFilterMap.externalId: String? get() =
    parseOrNull(EXTERNAL_ID_FILTER, String::class.java)

val SigningKeyFilterMap.createdAfter: Instant? get() =
    parseOrNull(CREATED_AFTER_FILTER, Instant::class.java)

val SigningKeyFilterMap.createdBefore: Instant? get() =
    parseOrNull(CREATED_BEFORE_FILTER, Instant::class.java)