package net.corda.membership.lib.impl.grouppolicy

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

@Suppress("ThrowsCount")
fun JsonNode.getMandatoryString(
    key: String
): String {
    if (!hasNonNull(key)) {
        throw BadGroupPolicyException(getMissingKeyError(key))
    }
    return get(key).run {
        if (!isTextual) {
            throw BadGroupPolicyException(getBadTypeError(key, "String"))
        }
        textValue().apply {
            if (isBlank()) {
                throw BadGroupPolicyException(getBlankValueError(key))
            }
        }
    }
}

fun JsonNode.getOptionalStringList(
    key: String
): List<String>? {
    val jsonList = getOptionalJsonNode(key)
    return if (jsonList == null || !jsonList.isArray) {
        null
    } else {
        jsonList.map {
            if (!it.isTextual) {
                throw BadGroupPolicyException(getBadTypeError(key, "String"))
            }
            it.textValue()
        }
    }
}

fun JsonNode.getMandatoryStringList(
    key: String
) = getOptionalStringList(key) ?: throw BadGroupPolicyException(getMissingKeyError(key))

fun JsonNode.getOptionalJsonNode(
    key: String
): JsonNode? = get(key)


fun JsonNode.getMandatoryJsonNode(
    key: String
) = getOptionalJsonNode(key) ?: throw BadGroupPolicyException(getMissingKeyError(key))

fun JsonNode.getOptionalStringMap(
    key: String
): Map<String, String>? {
    if (!hasNonNull(key)) {
        return null
    }
    return get(key).run {
        if (!isObject) {
            throw BadGroupPolicyException(getBadTypeError(key, "Object"))
        }
        ObjectMapper()
            .convertValue(
                this,
                object : TypeReference<Map<String, String>>() {}
            )
    }
}

fun JsonNode.getOptionalString(
    key: String
): String? {
    if (!hasNonNull(key)) {
        return null
    }
    return get(key).run {
        if (!isTextual) {
            throw BadGroupPolicyException(getBadTypeError(key, "Object"))
        }
        textValue()
    }
}

fun JsonNode.getMandatoryStringMap(
    key: String
) = getOptionalStringMap(key) ?: throw BadGroupPolicyException(getMissingKeyError(key))

fun <T> JsonNode.getMandatoryEnum(
    key: String,
    mapper: (String) -> T
): T {
    val value = getMandatoryString(key)
    return try {
        mapper(value)
    } catch (e: IllegalArgumentException) {
        throw BadGroupPolicyException(getBadEnumError(key, value))
    }
}

fun JsonNode.getMandatoryInt(
    key: String
): Int {
    if (!hasNonNull(key)) {
        throw BadGroupPolicyException(getMissingKeyError(key))
    }
    return get(key).run {
        if (!isInt) {
            throw BadGroupPolicyException(getMissingKeyError(key))
        }
        asInt()
    }
}

fun validatePemCert(pemCert: String, key: String, index: Int) {
    try {
        CertificateFactory.getInstance("X.509").generateCertificate(pemCert.byteInputStream())
    } catch (ex: CertificateException) {
        throw BadGroupPolicyException(getBadCertError(key, index))
    }
}
