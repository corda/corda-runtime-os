package net.corda.v5.application.flows;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FlowJavaApiTest {

    @Test
    public void callStringFlow() {
        final StringFlow flow = new StringFlow();

        String result = flow.call();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("do stuff");
    }

    @Test
    public void callVoidFlow() {
        final VoidFlow flow = new VoidFlow();

        Void result = flow.call();

        Assertions.assertThat(result).isNull();
    }


    static class StringFlow implements SubFlow<String> {

        @Override
        public String call() {
            return "do stuff";
        }
    }

    static class VoidFlow implements SubFlow<Void> {

        @Override
        public Void call() {
            return null;
        }
    }
}
