/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.data.flow;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

@org.apache.avro.specific.AvroGenerated
public class Identity extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 44743908861595912L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Identity\",\"namespace\":\"net.corda.data.flow\",\"fields\":[{\"name\":\"x500Name\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"group\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}]}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<Identity> ENCODER =
      new BinaryMessageEncoder<Identity>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<Identity> DECODER =
      new BinaryMessageDecoder<Identity>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<Identity> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<Identity> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<Identity> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<Identity>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this Identity to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a Identity from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a Identity instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static Identity fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private java.lang.String x500Name;
   private java.lang.String group;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public Identity() {}

  /**
   * All-args constructor.
   * @param x500Name The new value for x500Name
   * @param group The new value for group
   */
  public Identity(java.lang.String x500Name, java.lang.String group) {
    this.x500Name = x500Name;
    this.group = group;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return x500Name;
    case 1: return group;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: x500Name = value$ != null ? value$.toString() : null; break;
    case 1: group = value$ != null ? value$.toString() : null; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'x500Name' field.
   * @return The value of the 'x500Name' field.
   */
  public java.lang.String getX500Name() {
    return x500Name;
  }


  /**
   * Sets the value of the 'x500Name' field.
   * @param value the value to set.
   */
  public void setX500Name(java.lang.String value) {
    this.x500Name = value;
  }

  /**
   * Gets the value of the 'group' field.
   * @return The value of the 'group' field.
   */
  public java.lang.String getGroup() {
    return group;
  }


  /**
   * Sets the value of the 'group' field.
   * @param value the value to set.
   */
  public void setGroup(java.lang.String value) {
    this.group = value;
  }

  /**
   * Creates a new Identity RecordBuilder.
   * @return A new Identity RecordBuilder
   */
  public static net.corda.data.flow.Identity.Builder newBuilder() {
    return new net.corda.data.flow.Identity.Builder();
  }

  /**
   * Creates a new Identity RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new Identity RecordBuilder
   */
  public static net.corda.data.flow.Identity.Builder newBuilder(net.corda.data.flow.Identity.Builder other) {
    if (other == null) {
      return new net.corda.data.flow.Identity.Builder();
    } else {
      return new net.corda.data.flow.Identity.Builder(other);
    }
  }

  /**
   * Creates a new Identity RecordBuilder by copying an existing Identity instance.
   * @param other The existing instance to copy.
   * @return A new Identity RecordBuilder
   */
  public static net.corda.data.flow.Identity.Builder newBuilder(net.corda.data.flow.Identity other) {
    if (other == null) {
      return new net.corda.data.flow.Identity.Builder();
    } else {
      return new net.corda.data.flow.Identity.Builder(other);
    }
  }

  /**
   * RecordBuilder for Identity instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<Identity>
    implements org.apache.avro.data.RecordBuilder<Identity> {

    private java.lang.String x500Name;
    private java.lang.String group;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.data.flow.Identity.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.x500Name)) {
        this.x500Name = data().deepCopy(fields()[0].schema(), other.x500Name);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.group)) {
        this.group = data().deepCopy(fields()[1].schema(), other.group);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    /**
     * Creates a Builder by copying an existing Identity instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.data.flow.Identity other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.x500Name)) {
        this.x500Name = data().deepCopy(fields()[0].schema(), other.x500Name);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.group)) {
        this.group = data().deepCopy(fields()[1].schema(), other.group);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'x500Name' field.
      * @return The value.
      */
    public java.lang.String getX500Name() {
      return x500Name;
    }


    /**
      * Sets the value of the 'x500Name' field.
      * @param value The value of 'x500Name'.
      * @return This builder.
      */
    public net.corda.data.flow.Identity.Builder setX500Name(java.lang.String value) {
      validate(fields()[0], value);
      this.x500Name = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'x500Name' field has been set.
      * @return True if the 'x500Name' field has been set, false otherwise.
      */
    public boolean hasX500Name() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'x500Name' field.
      * @return This builder.
      */
    public net.corda.data.flow.Identity.Builder clearX500Name() {
      x500Name = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'group' field.
      * @return The value.
      */
    public java.lang.String getGroup() {
      return group;
    }


    /**
      * Sets the value of the 'group' field.
      * @param value The value of 'group'.
      * @return This builder.
      */
    public net.corda.data.flow.Identity.Builder setGroup(java.lang.String value) {
      validate(fields()[1], value);
      this.group = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'group' field has been set.
      * @return True if the 'group' field has been set, false otherwise.
      */
    public boolean hasGroup() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'group' field.
      * @return This builder.
      */
    public net.corda.data.flow.Identity.Builder clearGroup() {
      group = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Identity build() {
      try {
        Identity record = new Identity();
        record.x500Name = fieldSetFlags()[0] ? this.x500Name : (java.lang.String) defaultValue(fields()[0]);
        record.group = fieldSetFlags()[1] ? this.group : (java.lang.String) defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<Identity>
    WRITER$ = (org.apache.avro.io.DatumWriter<Identity>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<Identity>
    READER$ = (org.apache.avro.io.DatumReader<Identity>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    out.writeString(this.x500Name);

    if (this.group == null) {
      out.writeIndex(0);
      out.writeNull();
    } else {
      out.writeIndex(1);
      out.writeString(this.group);
    }

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.x500Name = in.readString();

      if (in.readIndex() != 1) {
        in.readNull();
        this.group = null;
      } else {
        this.group = in.readString();
      }

    } else {
      for (int i = 0; i < 2; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          this.x500Name = in.readString();
          break;

        case 1:
          if (in.readIndex() != 1) {
            in.readNull();
            this.group = null;
          } else {
            this.group = in.readString();
          }
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










