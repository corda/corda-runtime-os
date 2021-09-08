package net.corda.serialization

import net.corda.sandbox.SandboxGroup

/**
 * This builder will be the entry point to creating a [CheckpointSerializer].
 * Expected use case would be:
 *     builder
 *         .newCheckpointSerializer(sandboxGroup)
 *         .addSerializer(MyClass::java.class, MyClassSerializer())
 *         .addSerializerForClasses(listOf(ClassOne::class.java, ClassTwo::class.java, OtherSerializer())
 *         .build()
 *
 */
interface CheckpointSerializerBuilder {

    /**
     * This signals to the builder that a new [CheckpointSerializer] should be built.
     */
    fun newCheckpointSerializer(sandboxGroup: SandboxGroup): CheckpointSerializerBuilder

    /**
     * Used to add a custom serializer for a given class to the [CheckpointSerializer]
     * @param clazz the class which will be serialized using the given [serializer]
     * @param serializer the serializer implemented to serialize [clazz]
     */
    fun addSerializer(clazz: Class<*>, serializer: CheckpointInternalCustomSerializer<*>): CheckpointSerializerBuilder

    /**
     * Used to add a custom serializer for a set of classes to the [CheckpointSerializer]
     * @param classes the list of classes which will be serialized using the given [serializer]
     * @param serializer the serializer implemented to serialize each class in [classes]
     */
    fun addSerializerForClasses(classes: List<Class<*>>, serializer: CheckpointInternalCustomSerializer<*>): CheckpointSerializerBuilder

    /**
     * Builds and returns the configured [CheckpointSerializer]
     * @return the completed and ready [CheckpointSerializer]
     */
    fun build(): CheckpointSerializer
}
