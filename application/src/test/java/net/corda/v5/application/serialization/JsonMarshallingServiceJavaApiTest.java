package net.corda.v5.application.serialization;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JsonMarshallingServiceJavaApiTest {

    private final JsonMarshallingService jsonMarshallingService = mock(JsonMarshallingService.class);

    @Test
    public void formatJson() {
        final String test = "test";
        when(jsonMarshallingService.formatJson(any())).thenReturn(test);

        final String str = jsonMarshallingService.formatJson("{ property: value }");

        Assertions.assertThat(str).isNotNull();
        Assertions.assertThat(str).isEqualTo(test);
        verify(jsonMarshallingService, times(1)).formatJson(any());
    }

    @Test
    public void parseJson() {
        final ParseJson parseJson = new ParseJson();
        final String input = parseJson.toString();
        when(jsonMarshallingService.parseJson(input, ParseJson.class)).thenReturn(parseJson);

        final ParseJson parseJsonFromStr = jsonMarshallingService.parseJson(input, ParseJson.class);

        Assertions.assertThat(parseJsonFromStr).isNotNull();
        Assertions.assertThat(parseJsonFromStr).isEqualTo(parseJson);
        verify(jsonMarshallingService, times(1)).parseJson(input, ParseJson.class);
    }

    @Test
    public void parseJsonList() {
        final ParseJson parseJson = new ParseJson();
        final List<ParseJson> parseJsons = List.of(parseJson);
        final String input = parseJsons.toString();
        when(jsonMarshallingService.parseJsonList(input, ParseJson.class)).thenReturn(parseJsons);

        final List<ParseJson> parsedJsons = jsonMarshallingService.parseJsonList(input, ParseJson.class);

        Assertions.assertThat(parsedJsons).isNotNull();
        Assertions.assertThat(parsedJsons).isEqualTo(parseJsons);
        verify(jsonMarshallingService, times(1)).parseJsonList(input, ParseJson.class);
    }

    static class ParseJson {}

}
