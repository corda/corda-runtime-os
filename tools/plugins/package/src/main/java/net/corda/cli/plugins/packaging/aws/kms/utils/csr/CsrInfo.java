package net.corda.cli.plugins.packaging.aws.kms.utils.csr;

import lombok.Builder;

@Builder
public class CsrInfo {

    private final String cn;
    private final String ou;
    private final String o;
    private final String l;
    private final String st;
    private final String c;
    private final String mail;

    public String getCn() {
        return cn;
    }

    public String getOu() {
        return ou;
    }

    public String getO() {
        return o;
    }

    public String getL() {
        return l;
    }

    public String getSt() {
        return st;
    }

    public String getC() {
        return c;
    }

    public String getMail() {
        return mail;
    }

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
