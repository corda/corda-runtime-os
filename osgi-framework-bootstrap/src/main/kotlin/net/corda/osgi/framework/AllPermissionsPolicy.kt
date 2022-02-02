package net.corda.osgi.framework

import java.security.AllPermission
import java.security.CodeSource
import java.security.Permission
import java.security.PermissionCollection
import java.security.Policy
import java.util.Collections.enumeration
import java.util.Collections.unmodifiableList
import java.util.Enumeration
import kotlin.streams.toList

class AllPermissionsPolicy : Policy() {
    private companion object {
        private val allPermissions = AllPermissionsCollection()
    }

    override fun getPermissions(codeSource: CodeSource): PermissionCollection {
        return allPermissions
    }

    private class AllPermissionsCollection : PermissionCollection() {
        private companion object {
            private val allPermissionsElements = unmodifiableList(AllPermission()
                .newPermissionCollection()
                .elementsAsStream()
                .toList())
        }

        init {
            setReadOnly()
        }

        override fun add(permission: Permission) {}

        override fun implies(permission: Permission): Boolean = true

        override fun elements(): Enumeration<Permission> {
            return enumeration(allPermissionsElements)
        }
    }
}
