package de.haumacher.phoneblock.db.settings;

public class Contribution extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.Contribution} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.Contribution create() {
		return new de.haumacher.phoneblock.db.settings.Contribution();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.Contribution} type in JSON format. */
	public static final String CONTRIBUTION__TYPE = "Contribution";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getUserId() */
	public static final String USER_ID__PROP = "userId";

	/** @see #getSender() */
	public static final String SENDER__PROP = "sender";

	/** @see #getTx() */
	public static final String TX__PROP = "tx";

	/** @see #getAmount() */
	public static final String AMOUNT__PROP = "amount";

	/** @see #getMessage() */
	public static final String MESSAGE__PROP = "message";

	/** @see #getReceived() */
	public static final String RECEIVED__PROP = "received";

	/** @see #isAcknowledged() */
	public static final String ACKNOWLEDGED__PROP = "acknowledged";

	/** Identifier for the property {@link #getId()} in binary format. */
	static final int ID__ID = 1;

	/** Identifier for the property {@link #getUserId()} in binary format. */
	static final int USER_ID__ID = 2;

	/** Identifier for the property {@link #getSender()} in binary format. */
	static final int SENDER__ID = 3;

	/** Identifier for the property {@link #getTx()} in binary format. */
	static final int TX__ID = 4;

	/** Identifier for the property {@link #getAmount()} in binary format. */
	static final int AMOUNT__ID = 5;

	/** Identifier for the property {@link #getMessage()} in binary format. */
	static final int MESSAGE__ID = 6;

	/** Identifier for the property {@link #getReceived()} in binary format. */
	static final int RECEIVED__ID = 7;

	/** Identifier for the property {@link #isAcknowledged()} in binary format. */
	static final int ACKNOWLEDGED__ID = 8;

	private long _id = 0L;

	private Long _userId = null;

	private String _sender = "";

	private String _tx = "";

	private int _amount = 0;

	private String _message = "";

	private long _received = 0L;

	private boolean _acknowledged = false;

	/**
	 * Creates a {@link Contribution} instance.
	 *
	 * @see de.haumacher.phoneblock.db.settings.Contribution#create()
	 */
	protected Contribution() {
		super();
	}

	/**
	 * Internal ID of the contribution.
	 */
	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_listener.beforeSet(this, ID__PROP, value);
		_id = value;
	}

	/**
	 * ID of the user that donated (if known).
	 */
	public final Long getUserId() {
		return _userId;
	}

	/**
	 * @see #getUserId()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setUserId(Long value) {
		internalSetUserId(value);
		return this;
	}

	/** Internal setter for {@link #getUserId()} without chain call utility. */
	protected final void internalSetUserId(Long value) {
		_listener.beforeSet(this, USER_ID__PROP, value);
		_userId = value;
	}

	/**
	 * Checks, whether {@link #getUserId()} has a value.
	 */
	public final boolean hasUserId() {
		return _userId != null;
	}

	/**
	 * Name of the donator.
	 */
	public final String getSender() {
		return _sender;
	}

	/**
	 * @see #getSender()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setSender(String value) {
		internalSetSender(value);
		return this;
	}

	/** Internal setter for {@link #getSender()} without chain call utility. */
	protected final void internalSetSender(String value) {
		_listener.beforeSet(this, SENDER__PROP, value);
		_sender = value;
	}

	/**
	 * Transaction ID of the donation (external).
	 */
	public final String getTx() {
		return _tx;
	}

	/**
	 * @see #getTx()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setTx(String value) {
		internalSetTx(value);
		return this;
	}

	/** Internal setter for {@link #getTx()} without chain call utility. */
	protected final void internalSetTx(String value) {
		_listener.beforeSet(this, TX__PROP, value);
		_tx = value;
	}

	/**
	 * The amount in cent.
	 */
	public final int getAmount() {
		return _amount;
	}

	/**
	 * @see #getAmount()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setAmount(int value) {
		internalSetAmount(value);
		return this;
	}

	/** Internal setter for {@link #getAmount()} without chain call utility. */
	protected final void internalSetAmount(int value) {
		_listener.beforeSet(this, AMOUNT__PROP, value);
		_amount = value;
	}

	/**
	 * The optional message that was sent together with the donation.
	 */
	public final String getMessage() {
		return _message;
	}

	/**
	 * @see #getMessage()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setMessage(String value) {
		internalSetMessage(value);
		return this;
	}

	/** Internal setter for {@link #getMessage()} without chain call utility. */
	protected final void internalSetMessage(String value) {
		_listener.beforeSet(this, MESSAGE__PROP, value);
		_message = value;
	}

	/**
	 * The date, when the donation was received.
	 */
	public final long getReceived() {
		return _received;
	}

	/**
	 * @see #getReceived()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setReceived(long value) {
		internalSetReceived(value);
		return this;
	}

	/** Internal setter for {@link #getReceived()} without chain call utility. */
	protected final void internalSetReceived(long value) {
		_listener.beforeSet(this, RECEIVED__PROP, value);
		_received = value;
	}

	/**
	 * Whether an thank you e-mail was sent.
	 */
	public final boolean isAcknowledged() {
		return _acknowledged;
	}

	/**
	 * @see #isAcknowledged()
	 */
	public de.haumacher.phoneblock.db.settings.Contribution setAcknowledged(boolean value) {
		internalSetAcknowledged(value);
		return this;
	}

	/** Internal setter for {@link #isAcknowledged()} without chain call utility. */
	protected final void internalSetAcknowledged(boolean value) {
		_listener.beforeSet(this, ACKNOWLEDGED__PROP, value);
		_acknowledged = value;
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.settings.Contribution registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.settings.Contribution unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return CONTRIBUTION__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			ID__PROP, 
			USER_ID__PROP, 
			SENDER__PROP, 
			TX__PROP, 
			AMOUNT__PROP, 
			MESSAGE__PROP, 
			RECEIVED__PROP, 
			ACKNOWLEDGED__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case ID__PROP: return getId();
			case USER_ID__PROP: return getUserId();
			case SENDER__PROP: return getSender();
			case TX__PROP: return getTx();
			case AMOUNT__PROP: return getAmount();
			case MESSAGE__PROP: return getMessage();
			case RECEIVED__PROP: return getReceived();
			case ACKNOWLEDGED__PROP: return isAcknowledged();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case ID__PROP: internalSetId((long) value); break;
			case USER_ID__PROP: internalSetUserId((Long) value); break;
			case SENDER__PROP: internalSetSender((String) value); break;
			case TX__PROP: internalSetTx((String) value); break;
			case AMOUNT__PROP: internalSetAmount((int) value); break;
			case MESSAGE__PROP: internalSetMessage((String) value); break;
			case RECEIVED__PROP: internalSetReceived((long) value); break;
			case ACKNOWLEDGED__PROP: internalSetAcknowledged((boolean) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.Contribution readContribution(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.Contribution result = new de.haumacher.phoneblock.db.settings.Contribution();
		result.readContent(in);
		return result;
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		writeContent(out);
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(ID__PROP);
		out.value(getId());
		if (hasUserId()) {
			out.name(USER_ID__PROP);
			out.value(getUserId());
		}
		out.name(SENDER__PROP);
		out.value(getSender());
		out.name(TX__PROP);
		out.value(getTx());
		out.name(AMOUNT__PROP);
		out.value(getAmount());
		out.name(MESSAGE__PROP);
		out.value(getMessage());
		out.name(RECEIVED__PROP);
		out.value(getReceived());
		out.name(ACKNOWLEDGED__PROP);
		out.value(isAcknowledged());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case ID__PROP: setId(in.nextLong()); break;
			case USER_ID__PROP: setUserId(in.nextLong()); break;
			case SENDER__PROP: setSender(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TX__PROP: setTx(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case AMOUNT__PROP: setAmount(in.nextInt()); break;
			case MESSAGE__PROP: setMessage(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case RECEIVED__PROP: setReceived(in.nextLong()); break;
			case ACKNOWLEDGED__PROP: setAcknowledged(in.nextBoolean()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public final void writeTo(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.beginObject();
		writeFields(out);
		out.endObject();
	}

	/**
	 * Serializes all fields of this instance to the given binary output.
	 *
	 * @param out
	 *        The binary output to write to.
	 * @throws java.io.IOException If writing fails.
	 */
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		out.name(ID__ID);
		out.value(getId());
		if (hasUserId()) {
			out.name(USER_ID__ID);
			out.value(getUserId());
		}
		out.name(SENDER__ID);
		out.value(getSender());
		out.name(TX__ID);
		out.value(getTx());
		out.name(AMOUNT__ID);
		out.value(getAmount());
		out.name(MESSAGE__ID);
		out.value(getMessage());
		out.name(RECEIVED__ID);
		out.value(getReceived());
		out.name(ACKNOWLEDGED__ID);
		out.value(isAcknowledged());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.Contribution readContribution(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.db.settings.Contribution result = de.haumacher.phoneblock.db.settings.Contribution.readContribution_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.db.settings.Contribution} from a polymorphic composition. */
	public static de.haumacher.phoneblock.db.settings.Contribution readContribution_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.Contribution result = new Contribution();
		result.readContent(in);
		return result;
	}

	/** Helper for reading all fields of this instance. */
	protected final void readContent(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		while (in.hasNext()) {
			int field = in.nextName();
			readField(in, field);
		}
	}

	/** Consumes the value for the field with the given ID and assigns its value. */
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case ID__ID: setId(in.nextLong()); break;
			case USER_ID__ID: setUserId(in.nextLong()); break;
			case SENDER__ID: setSender(in.nextString()); break;
			case TX__ID: setTx(in.nextString()); break;
			case AMOUNT__ID: setAmount(in.nextInt()); break;
			case MESSAGE__ID: setMessage(in.nextString()); break;
			case RECEIVED__ID: setReceived(in.nextLong()); break;
			case ACKNOWLEDGED__ID: setAcknowledged(in.nextBoolean()); break;
			default: in.skipValue(); 
		}
	}

}
