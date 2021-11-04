package net.corda.libs.permission


/**
 * Example format https://host:1234/node-rpc/<uuid>/flow/start/com.example.MyFlow
 */
internal data class PermissionUrl(val uuid: String, val permissionRequested: String ) {
    companion object Factory {

        fun fromUrl(url: String): PermissionUrl {
            val uuidAndPerm = url.split("/node-rpc/").last()
            val uuidAndPermList = uuidAndPerm.split("/".toRegex(), 2)
            return PermissionUrl(uuidAndPermList.first(), uuidAndPermList.last())
        }


    }
}