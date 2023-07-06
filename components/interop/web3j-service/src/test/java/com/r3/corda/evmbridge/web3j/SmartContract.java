package com.r3.corda.evmbridge.web3j;

import java.util.List;

public interface SmartContract {
    class Input {
        public static class Builder {
            public String name;
            public String type;
            public Object value;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder type(String type) {
                this.type = type;
                return this;
            }

            public Builder value(Object value) {
                this.value = value;
                return this;
            }

            public Input build() {
                if (name == null || type == null || value == null) {
                    throw new IllegalArgumentException("Not all values for Input specified");
                }
                return new Input(name, type, value);
            }
        }

        private Input(String name, String type, Object value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }
        public String name;
        public String type;
        public Object value;

        @Override
        public String toString() {
            return "Input{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", value=" + value +
                    '}';
        }
    }

    void execute(String functionName);
    void execute(String functionName, List<Input> parameters);
}
