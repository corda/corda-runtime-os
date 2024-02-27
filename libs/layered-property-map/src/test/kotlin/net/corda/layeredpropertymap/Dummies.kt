package net.corda.layeredpropertymap

import net.corda.crypto.core.parseSecureHash
import net.corda.v5.crypto.SecureHash
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class DummyNumber(val number: Int)

data class DummyObjectWithNumberAndText(val number: Int, val text: String)

class DummyConverter : CustomPropertyConverter<DummyObjectWithNumberAndText> {
    override val type: Class<DummyObjectWithNumberAndText>
        get() = DummyObjectWithNumberAndText::class.java

    override fun convert(context: ConversionContext): DummyObjectWithNumberAndText {
        return DummyObjectWithNumberAndText(
            context.value("number")?.toInt() ?: throw NullPointerException(),
            context.value("text") ?: throw NullPointerException()
        )
    }
}

data class DummyEndpointInfo(val url: String, val protocolVersion: Int)

class DummyEndpointInfoConverter : CustomPropertyConverter<DummyEndpointInfo> {
    companion object {
        private const val URL_KEY = "url"
        private const val PROTOCOL_VERSION_KEY = "protocolVersion"
    }

    override val type: Class<DummyEndpointInfo>
        get() = DummyEndpointInfo::class.java

    override fun convert(context: ConversionContext): DummyEndpointInfo {
        if (context.key.isEmpty()) {
            assertTrue(context.isListItem)
        } else {
            assertFalse(context.isListItem)
        }
        return DummyEndpointInfo(
            context.value(URL_KEY)
                ?: throw IllegalArgumentException("$URL_KEY cannot be null."),
            context.value(PROTOCOL_VERSION_KEY)?.toInt()
                ?: throw IllegalArgumentException("$PROTOCOL_VERSION_KEY cannot be null.")
        )
    }
}

class DummyPublicKeyHashConverter : CustomPropertyConverter<SecureHash> {
    override val type: Class<SecureHash>
        get() = SecureHash::class.java

    override fun convert(context: ConversionContext): SecureHash? {
        if (context.key.isEmpty()) {
            assertTrue(context.isListItem)
        } else {
            assertFalse(context.isListItem)
        }
        return context.value()?.let { parseSecureHash(it) }
    }
}
