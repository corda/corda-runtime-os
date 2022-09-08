package net.corda.v5.serialization.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to mark an enumerated type as having had a new constant appended to it. For
 * each additional constant added a new annotation should be appended to the class. If more than one
 * is required the wrapper annotation {@link CordaSerializationTransformEnumDefaults} should be used to
 * encapsulate them
 * <p/>
 * For Example:<p/>
 * Enum before modification:
 * <pre>
 *  enum class ExampleEnum {
 *    A, B, C
 *  }
 * </pre>
 * <p/>
 * Assuming at some point a new constant is added it is required we have some mechanism by which to tell
 * nodes with an older version of the class on their Class Path what to do if they attempt to deserialize
 * an example of the class with that new value
 * <p/>
 * <pre>
 *  &#64;CordaSerializationTransformEnumDefault(newName = "D", oldName = "C")
 *  enum class ExampleEnum {
 *    A, B, C, D
 *  }
 * </pre>
 * <p/>
 * So, on deserialisation treat any instance of the enum that is encoded as D as C
 * <p/>
 * Adding a second new constant requires the wrapper annotation {@link CordaSerializationTransformEnumDefaults}
 * <pre>
 *  &#64;CordaSerializationTransformEnumDefaults(
 *      &#64;CordaSerializationTransformEnumDefault(newName = "E", oldName = "D"),
 *      &#64;CordaSerializationTransformEnumDefault(newName = "D", oldName = "C")
 *  )
 *  enum class ExampleEnum {
 *    A, B, C, D, E
 *  }
 * </pre>
 *
 * It's fine to assign the second new value a default that may not be present in all versions as in this
 * case it will work down the transform hierarchy until it finds a value it can apply, in this case it would
 * try E -> D -> C (when E -> D fails)
 */
@Target(TYPE)
@Retention(RUNTIME)
@Repeatable(CordaSerializationTransformEnumDefaults.class)
public @interface CordaSerializationTransformEnumDefault {
    /**
     *  @return new {@link String} equivalent of the value of the new constant
     */
    String newName();

    /**
     * @return old {@link String} equivalent of the value of the existing constant that deserialisers should
     * favour when de-serialising a value they have no corresponding value for
     */
    String oldName();
}
