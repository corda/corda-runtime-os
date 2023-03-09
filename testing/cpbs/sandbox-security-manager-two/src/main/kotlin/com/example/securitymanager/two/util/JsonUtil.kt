@file:Suppress("deprecation")
package com.example.securitymanager.two.util

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.AccessController
import java.security.PrivilegedAction

class JsonUtil {
    companion object {
        fun privilegedToJson(o: Any): String {
            return AccessController.doPrivileged(
                PrivilegedAction {
                    val mapper = ObjectMapper()
                    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                    mapper.writeValueAsString(o)
                }
            )
        }
    }
}
