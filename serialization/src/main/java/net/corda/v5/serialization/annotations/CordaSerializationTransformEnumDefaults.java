package net.corda.v5.serialization.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to mark an enumerated type as having had multiple members added, It acts
 * as a container annotation for instances of {@link CordaSerializationTransformEnumDefault}, each of which
 * details individual additions.
 *
 * NOTE: Order is important, new values should always be added before any others
 *
 * <pre/>
 *  // initial implementation
 *  enum class ExampleEnum {
 *    A, B, C
 *  }
 *
 *  // First alteration
 *  &#64;CordaSerializationTransformEnumDefaults(
 *      &#64;CordaSerializationTransformEnumDefault(newName = "D", oldName = "C"))
 *  enum class ExampleEnum {
 *    A, B, C, D
 *  }
 *
 *  // Second alteration, new transform is placed at the head of the list
 *  &#64;CordaSerializationTransformEnumDefaults(
 *      &#64;CordaSerializationTransformEnumDefault(newName = "E", oldName = "C"),
 *      &#64;CordaSerializationTransformEnumDefault(newName = "D", oldName = "C"))
 *  enum class ExampleEnum {
 *    A, B, C, D, E
 *  }
 * </pre>
 * <p/>
 * IMPORTANT - Once added (and in production) do NOT remove old annotations. See documentation for
 * more discussion on this point!.
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface CordaSerializationTransformEnumDefaults {
    /**
     * @return value an array of {@link CordaSerializationTransformEnumDefault}.
     */
    CordaSerializationTransformEnumDefault[] value();
}
