package net.corda.internal.serialization.amqp;

import java.util.List;

/**
 * The class lacks a public constructor that takes parameters it can associate
 * with its properties and is thus not serializable by the CORDA serialization
 * framework.
 */
public class ClassThatNeedsCustomSerializer {
    private final Integer a;
    private final Integer b;

    Integer getA() {
        return a;
    }

    Integer getB() {
        return b;
    }

    public ClassThatNeedsCustomSerializer(List<Integer> l) {
        this.a = l.get(0);
        this.b = l.get(1);
    }
}
