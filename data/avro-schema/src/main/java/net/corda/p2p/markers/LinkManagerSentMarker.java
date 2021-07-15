/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.p2p.markers;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

/** The message was received by the LinkManager (on P2P_OUT_TOPIC) from the application level. */
@org.apache.avro.specific.AvroGenerated
public class LinkManagerSentMarker extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -2537506531453178306L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"LinkManagerSentMarker\",\"namespace\":\"net.corda.p2p.markers\",\"doc\":\"The message was received by the LinkManager (on P2P_OUT_TOPIC) from the application level.\",\"fields\":[{\"name\":\"partition\",\"type\":\"long\",\"doc\":\"The original partition of the message in P2P_OUT_TOPIC.\"},{\"name\":\"offset\",\"type\":\"long\",\"doc\":\"The original offset of the message in P2P_OUT_TOPIC.\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<LinkManagerSentMarker> ENCODER =
      new BinaryMessageEncoder<LinkManagerSentMarker>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<LinkManagerSentMarker> DECODER =
      new BinaryMessageDecoder<LinkManagerSentMarker>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<LinkManagerSentMarker> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<LinkManagerSentMarker> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<LinkManagerSentMarker> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<LinkManagerSentMarker>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this LinkManagerSentMarker to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a LinkManagerSentMarker from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a LinkManagerSentMarker instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static LinkManagerSentMarker fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  /** The original partition of the message in P2P_OUT_TOPIC. */
   private long partition;
  /** The original offset of the message in P2P_OUT_TOPIC. */
   private long offset;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public LinkManagerSentMarker() {}

  /**
   * All-args constructor.
   * @param partition The original partition of the message in P2P_OUT_TOPIC.
   * @param offset The original offset of the message in P2P_OUT_TOPIC.
   */
  public LinkManagerSentMarker(java.lang.Long partition, java.lang.Long offset) {
    this.partition = partition;
    this.offset = offset;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return partition;
    case 1: return offset;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: partition = (java.lang.Long)value$; break;
    case 1: offset = (java.lang.Long)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'partition' field.
   * @return The original partition of the message in P2P_OUT_TOPIC.
   */
  public long getPartition() {
    return partition;
  }


  /**
   * Sets the value of the 'partition' field.
   * The original partition of the message in P2P_OUT_TOPIC.
   * @param value the value to set.
   */
  public void setPartition(long value) {
    this.partition = value;
  }

  /**
   * Gets the value of the 'offset' field.
   * @return The original offset of the message in P2P_OUT_TOPIC.
   */
  public long getOffset() {
    return offset;
  }


  /**
   * Sets the value of the 'offset' field.
   * The original offset of the message in P2P_OUT_TOPIC.
   * @param value the value to set.
   */
  public void setOffset(long value) {
    this.offset = value;
  }

  /**
   * Creates a new LinkManagerSentMarker RecordBuilder.
   * @return A new LinkManagerSentMarker RecordBuilder
   */
  public static net.corda.p2p.markers.LinkManagerSentMarker.Builder newBuilder() {
    return new net.corda.p2p.markers.LinkManagerSentMarker.Builder();
  }

  /**
   * Creates a new LinkManagerSentMarker RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new LinkManagerSentMarker RecordBuilder
   */
  public static net.corda.p2p.markers.LinkManagerSentMarker.Builder newBuilder(net.corda.p2p.markers.LinkManagerSentMarker.Builder other) {
    if (other == null) {
      return new net.corda.p2p.markers.LinkManagerSentMarker.Builder();
    } else {
      return new net.corda.p2p.markers.LinkManagerSentMarker.Builder(other);
    }
  }

  /**
   * Creates a new LinkManagerSentMarker RecordBuilder by copying an existing LinkManagerSentMarker instance.
   * @param other The existing instance to copy.
   * @return A new LinkManagerSentMarker RecordBuilder
   */
  public static net.corda.p2p.markers.LinkManagerSentMarker.Builder newBuilder(net.corda.p2p.markers.LinkManagerSentMarker other) {
    if (other == null) {
      return new net.corda.p2p.markers.LinkManagerSentMarker.Builder();
    } else {
      return new net.corda.p2p.markers.LinkManagerSentMarker.Builder(other);
    }
  }

  /**
   * RecordBuilder for LinkManagerSentMarker instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<LinkManagerSentMarker>
    implements org.apache.avro.data.RecordBuilder<LinkManagerSentMarker> {

    /** The original partition of the message in P2P_OUT_TOPIC. */
    private long partition;
    /** The original offset of the message in P2P_OUT_TOPIC. */
    private long offset;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.p2p.markers.LinkManagerSentMarker.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.partition)) {
        this.partition = data().deepCopy(fields()[0].schema(), other.partition);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.offset)) {
        this.offset = data().deepCopy(fields()[1].schema(), other.offset);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    /**
     * Creates a Builder by copying an existing LinkManagerSentMarker instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.p2p.markers.LinkManagerSentMarker other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.partition)) {
        this.partition = data().deepCopy(fields()[0].schema(), other.partition);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.offset)) {
        this.offset = data().deepCopy(fields()[1].schema(), other.offset);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'partition' field.
      * The original partition of the message in P2P_OUT_TOPIC.
      * @return The value.
      */
    public long getPartition() {
      return partition;
    }


    /**
      * Sets the value of the 'partition' field.
      * The original partition of the message in P2P_OUT_TOPIC.
      * @param value The value of 'partition'.
      * @return This builder.
      */
    public net.corda.p2p.markers.LinkManagerSentMarker.Builder setPartition(long value) {
      validate(fields()[0], value);
      this.partition = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'partition' field has been set.
      * The original partition of the message in P2P_OUT_TOPIC.
      * @return True if the 'partition' field has been set, false otherwise.
      */
    public boolean hasPartition() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'partition' field.
      * The original partition of the message in P2P_OUT_TOPIC.
      * @return This builder.
      */
    public net.corda.p2p.markers.LinkManagerSentMarker.Builder clearPartition() {
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'offset' field.
      * The original offset of the message in P2P_OUT_TOPIC.
      * @return The value.
      */
    public long getOffset() {
      return offset;
    }


    /**
      * Sets the value of the 'offset' field.
      * The original offset of the message in P2P_OUT_TOPIC.
      * @param value The value of 'offset'.
      * @return This builder.
      */
    public net.corda.p2p.markers.LinkManagerSentMarker.Builder setOffset(long value) {
      validate(fields()[1], value);
      this.offset = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'offset' field has been set.
      * The original offset of the message in P2P_OUT_TOPIC.
      * @return True if the 'offset' field has been set, false otherwise.
      */
    public boolean hasOffset() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'offset' field.
      * The original offset of the message in P2P_OUT_TOPIC.
      * @return This builder.
      */
    public net.corda.p2p.markers.LinkManagerSentMarker.Builder clearOffset() {
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public LinkManagerSentMarker build() {
      try {
        LinkManagerSentMarker record = new LinkManagerSentMarker();
        record.partition = fieldSetFlags()[0] ? this.partition : (java.lang.Long) defaultValue(fields()[0]);
        record.offset = fieldSetFlags()[1] ? this.offset : (java.lang.Long) defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<LinkManagerSentMarker>
    WRITER$ = (org.apache.avro.io.DatumWriter<LinkManagerSentMarker>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<LinkManagerSentMarker>
    READER$ = (org.apache.avro.io.DatumReader<LinkManagerSentMarker>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    out.writeLong(this.partition);

    out.writeLong(this.offset);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.partition = in.readLong();

      this.offset = in.readLong();

    } else {
      for (int i = 0; i < 2; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          this.partition = in.readLong();
          break;

        case 1:
          this.offset = in.readLong();
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










