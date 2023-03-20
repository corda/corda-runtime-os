package net.corda.rest.test

import net.corda.rest.PluggableRestResource

class CustomSerializationAPIImpl : CustomSerializationAPI, PluggableRestResource<CustomSerializationAPI> {
    override val targetInterface: Class<CustomSerializationAPI>
        get() = CustomSerializationAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun printString(s: CustomString): CustomString {
        return s
    }

    override fun printUnsafeString(s: CustomUnsafeString): CustomUnsafeString {
        return s
    }

    override fun printCustomMarshalString(s: CustomMarshalString): CustomMarshalString {
        return s
    }
}