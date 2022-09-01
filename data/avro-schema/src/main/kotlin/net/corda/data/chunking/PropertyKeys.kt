package net.corda.data.chunking

object PropertyKeys {

    /**
     * Boolean property specifying whether same data content can re-upload again
     */
    const val FORCE_UPLOAD = "forceUpload"
    /**
     * Boolean property specifying whether the vnode databases should be deleted and recreated
     */
    const val RESET_DB = "resetDb"
    /**
     * String property specifying the RPC actor that made the request
     */
    const val ACTOR = "actor"
}
