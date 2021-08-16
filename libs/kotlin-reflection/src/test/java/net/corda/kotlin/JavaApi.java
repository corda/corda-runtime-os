package net.corda.kotlin;

import java.util.List;

@SuppressWarnings("unused")
public interface JavaApi {
    long API_CONST = 123456;
    Object API_FIELD = "Not CONST";

    int getInteger();

    void setValue(String data);

    List<String> modify(List<String> inputs, String... values);
}
