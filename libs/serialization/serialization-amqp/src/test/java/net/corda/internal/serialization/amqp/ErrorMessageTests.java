package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.NotSerializableException;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("Current behaviour allows for the serialization of objects with private members, this will be disallowed at some point in the future")
public class ErrorMessageTests {
    private String errMsg(String property, String testname) {
        return "Property '"
                + property
                + "' or its getter is non public, this renders class 'class "
                + testname
                + "$C' unserializable -> class "
                + testname
                + "$C";
    }

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
