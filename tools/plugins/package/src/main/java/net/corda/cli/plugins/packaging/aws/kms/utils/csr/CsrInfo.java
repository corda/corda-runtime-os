package net.corda.cli.plugins.packaging.aws.kms.utils.csr;

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

    private CsrInfo(CsrInfoBuilder builder) {
        this.cn = builder.cn;
        this.ou = builder.ou;
        this.o = builder.o;
        this.l = builder.l;
        this.st = builder.st;
        this.c = builder.c;
        this.mail = builder.mail;
    }

    // Builder class
    public static class CsrInfoBuilder {
        private String cn;
        private String ou;
        private String o;
        private String l;
        private String st;
        private String c;
        private String mail;

        public CsrInfoBuilder() {
        }

        public CsrInfoBuilder cn(String cn) {
            this.cn = cn;
            return this;
        }

        public CsrInfoBuilder ou(String ou) {
            this.ou = ou;
            return this;
        }

        public CsrInfoBuilder o(String o) {
            this.o = o;
            return this;
        }

        public CsrInfoBuilder l(String l) {
            this.l = l;
            return this;
        }

        public CsrInfoBuilder st(String st) {
            this.st = st;
            return this;
        }

        public CsrInfoBuilder c(String c) {
            this.c = c;
            return this;
        }

        public CsrInfoBuilder mail(String mail) {
            this.mail = mail;
            return this;
        }

        public CsrInfo build() {
            return new CsrInfo(this);
        }
    }
}
