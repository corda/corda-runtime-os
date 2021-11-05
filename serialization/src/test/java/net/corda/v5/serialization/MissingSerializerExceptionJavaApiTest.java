package net.corda.v5.serialization;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MissingSerializerExceptionJavaApiTest {

    @Test
    public void getMessage() {
        MissingSerializerException missingSerializerException = new MissingSerializerException("message", "typeDescriptor");
        var result = missingSerializerException.getMessage();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("message");
    }

    @Test
    public void getTypeDescriptor() {
        MissingSerializerException missingSerializerException = new MissingSerializerException("message", "typeDescriptor");
        var result = missingSerializerException.getTypeDescriptor();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("typeDescriptor");
    }


    @Test
    public void getTypeNames() {
        MissingSerializerException missingSerializerException = new MissingSerializerException("message", List.of("typeNames"));
        var result = missingSerializerException.getTypeNames();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(List.of("typeNames"));
    }

}
