package net.corda.v5.application.messaging;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class UntrustworthyDataJavaApiTest {

    @Test
    public void unwrapString() {
        final String string  = "str";
        final UntrustworthyData<String> stringUntrustworthyData = new UntrustworthyData<>(string);
        final String str = stringUntrustworthyData.unwrap(v -> v);

        Assertions.assertThat(str).isNotNull();
        Assertions.assertThat(str).isEqualTo(string);
    }

    @Test
    public void unwrapInteger() {
        final Integer number  = 1;
        final UntrustworthyData<Integer> integerUntrustworthyData = new UntrustworthyData<>(number);
        final Integer integer = integerUntrustworthyData.unwrap(v -> v);

        Assertions.assertThat(integer).isNotNull();
        Assertions.assertThat(integer).isEqualTo(number);
    }

    @Test
    public void unwrapCustomClass() {
        final Untrustworthy untrustworthy  = new Untrustworthy();
        final UntrustworthyData<Untrustworthy> integerUntrustworthyData = new UntrustworthyData<>(untrustworthy);
        final Untrustworthy untrustworthyTest = integerUntrustworthyData.unwrap(v -> v);

        Assertions.assertThat(untrustworthyTest).isNotNull();
        Assertions.assertThat(untrustworthyTest).isEqualTo(untrustworthy);
    }

    @Test
    public void unwrap_withCustomClass_returnDifferentValue() {
        final String hello = "hello";
        final Untrustworthy untrustworthy  = new Untrustworthy();
        final UntrustworthyData<Untrustworthy> integerUntrustworthyData = new UntrustworthyData<>(untrustworthy);
        final String helloTest = integerUntrustworthyData.unwrap(v -> hello);

        Assertions.assertThat(helloTest).isNotNull();
        Assertions.assertThat(helloTest).isEqualTo(hello);
    }

    static class Untrustworthy { }
}
