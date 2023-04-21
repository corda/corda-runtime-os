package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.helper.TestSerializationContext;
import net.corda.v5.base.annotations.CordaSerializable;
import org.junit.jupiter.api.Test;

import java.io.NotSerializableException;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ErrorMessageTests {
    private String errMsg(String property, String testname) {
        return "Unable to create an object serializer for type class "
                + testname + "$C:\n" +
                "Mandatory constructor parameters [" + property + "] are missing from the readable properties []\n\n"
                + "Either provide getters or readable fields for [" + property + "], or provide a custom serializer for this type\n\n"
                + "No custom serializers registered.\n";
    }


    @CordaSerializable
    static class C {
        public Integer a;

        public C(Integer a) {
            this.a = a;
        }

        private Integer getA() { return this.a; }
    }

    @Test
    public void testJavaConstructorAnnotations() {
        SerializationOutput ser = new SerializationOutput(testDefaultFactory());

        assertThatThrownBy(() -> ser.serialize(new C(1), TestSerializationContext.testSerializationContext))
                .isInstanceOf(NotSerializableException.class)
                .hasMessage(errMsg("a", getClass().getName()));
    }
}
