package net.corda.crypto.persistence

enum class SigningKeyOrderBy {
    NONE,
    CREATED,
    CATEGORY,
    SCHEME_CODE_NAME,
    ALIAS,
    MASTER_KEY_ALIAS,
    EXTERNAL_ID,
    CREATED_DESC,
    CATEGORY_DESC,
    SCHEME_CODE_NAME_DESC,
    ALIAS_DESC,
    MASTER_KEY_ALIAS_DESC,
    EXTERNAL_ID_DESC
}