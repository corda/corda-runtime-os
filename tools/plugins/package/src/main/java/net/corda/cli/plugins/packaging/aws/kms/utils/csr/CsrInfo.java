package software.amazon.awssdk.services.kms.jce.util.csr;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CsrInfo {

    private final String cn;
    private final String ou;
    private final String o;
    private final String l;
    private final String st;
    private final String c;
    private final String mail;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        append(builder, "CN", cn);
        append(builder, "OU", ou);
        append(builder, "O", o);
        append(builder, "L", l);
        append(builder, "ST", st);
        append(builder, "C", c);
        append(builder, "emailAddress", mail);

        return builder.toString();
    }

    private void append(StringBuilder builder, String attribute, String value) {
        if (value != null) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(attribute).append("=").append(value);
        }
    }
}
