package net.corda.libs.permission.impl

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PermissionUrlTest {

    @Test
    fun `can create permissionUrl from valid string`() {
        val permissionUrl = PermissionUrl.fromUrl(
            "https://host:1234/node-rpc/5e0a07a6-c25d-413a-be34-647a792f4f58/flow/start/com.example.MyFlow")
        assertEquals("5e0a07a6-c25d-413a-be34-647a792f4f58", permissionUrl.uuid)
        assertEquals("flow/start/com.example.MyFlow", permissionUrl.permissionRequested)
    }
}