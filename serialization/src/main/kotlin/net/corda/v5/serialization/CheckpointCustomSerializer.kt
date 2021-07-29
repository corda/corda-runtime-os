package net.corda.v5.serialization

/**
 * Allows CorDapps to provide custom serializers for classes that do not serialize successfully during a checkpoint.
 * In this case, a proxy serializer can be written that implements this interface whose purpose is to move between
 * unserializable types and an intermediate representation.
 *
 * NOTE: Only implement this interface if you have a class that triggers an error during normal checkpoint
 * serialization/deserialization.
 */
interface CheckpointCustomSerializer<OBJ, PROXY> {
    /**
     * Should facilitate the conversion of the third party object into the serializable
     * local class specified by [PROXY]
     */
    fun toProxy(obj: OBJ): PROXY

    /**
     * Should facilitate the conversion of the proxy object into a new instance of the
     * unserializable type
     */
    fun fromProxy(proxy: PROXY): OBJ
}
