package net.corda.crypto.service

enum class KeyOrderBy {
    NONE,
    ID,
    TIMESTAMP,
    CATEGORY,
    SCHEME_CODE_NAME,
    ALIAS,
    MASTER_KEY_ALIAS,
    EXTERNAL_ID,
    ID_DESC,
    TIMESTAMP_DESC,
    CATEGORY_DESC,
    SCHEME_CODE_NAME_DESC,
    ALIAS_DESC,
    MASTER_KEY_ALIAS_DESC,
    EXTERNAL_ID_DESC
}