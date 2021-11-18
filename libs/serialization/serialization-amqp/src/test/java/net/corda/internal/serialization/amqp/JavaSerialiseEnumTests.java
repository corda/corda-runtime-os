package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.testutils.TestSerializationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.concurrent.TimeUnit;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class JavaSerialiseEnumTests {

    public enum Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    private static class Bra {
        private final Bras bra;

        private Bra(Bras bra) {
            this.bra = bra;
        }

        public Bras getBra() {
            return this.bra;
        }
    }

    @Test
    public void testJavaConstructorAnnotations() throws NotSerializableException {
        Bra bra = new Bra(Bras.UNDERWIRE);

        SerializationOutput ser = new SerializationOutput(testDefaultFactory());
        ser.serialize(bra, TestSerializationContext.getTestSerializationContext());
    }
}
