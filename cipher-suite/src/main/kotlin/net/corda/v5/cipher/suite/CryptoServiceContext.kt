@file:JvmName("CryptoServiceContext")

package net.corda.v5.cipher.suite

/**
 * Standard [CryptoService] context key defining the tenant id on which behalf the corresponding operation is called.
 */
const val CRYPTO_TENANT_ID = "tenantId"

/**
 * Standard [CryptoService] context key defining the category for which the key is generated.
 */
const val CRYPTO_CATEGORY = "category"