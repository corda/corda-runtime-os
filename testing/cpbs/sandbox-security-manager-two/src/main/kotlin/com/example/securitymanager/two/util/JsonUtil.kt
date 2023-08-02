package com.example.securitymanager.two.util

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.PrivilegedAction

class JsonUtil {
    companion object {
        fun privilegedToJson(o: Any): String {
            @Suppress("deprecation", "removal")
            return java.security.AccessController.doPrivileged(
                PrivilegedAction<String> {
                    val mapper = ObjectMapper()
                    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                    mapper.writeValueAsString(o)
                }
            )
        }
    }
}