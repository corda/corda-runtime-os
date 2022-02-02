package net.corda.serialization.checkpoint

import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * This builder will be the entry point to creating a [CheckpointSerializer].
 * Expected use case would be:
 *     builder
 *         .addSerializer(MyClass::java.class, MyClassSerializer())
 *         .addSerializerForClasses(listOf(ClassOne::class.java, ClassTwo::class.java, OtherSerializer())
 *         .addSingletonSerializableInstances(setOf(SingletonOne::class.java, SingletonTwo::class.java))
 *         .build()
 *
 */
interface CheckpointSerializerBuilder {

    /**
     * Used to add a custom serializer for a given class to the [CheckpointSerializer]
     * @param clazz the class which will be serialized using the given [serializer]
     * @param serializer the serializer implemented to serialize [clazz]
     */
    fun addSerializer(
        clazz: Class<*>,
        serializer: CheckpointInternalCustomSerializer<*>
    ): CheckpointSerializerBuilder

    /**
     * Used to add a custom serializer for a set of classes to the [CheckpointSerializer]
     * @param classes the list of classes which will be serialized using the given [serializer]
     * @param serializer the serializer implemented to serialize each class in [classes]
     */
    fun addSerializerForClasses(
        classes: List<Class<*>>,
        serializer: CheckpointInternalCustomSerializer<*>
    ): CheckpointSerializerBuilder

    /**
     * Used to add any [SingletonSerializeAsToken] interfaces to the serializer.  Only one instance per
     * class should be used (i.e. these should be Singleton classes).
     *
     * @param instances any [SingletonSerializeAsToken] instances to be added for serialization.
     */
    fun addSingletonSerializableInstances(instances: Set<SingletonSerializeAsToken>): CheckpointSerializerBuilder

    /**
     * Builds and returns the configured [CheckpointSerializer]
     * @return the completed and ready [CheckpointSerializer]
     */
    fun build(): CheckpointSerializer
}
