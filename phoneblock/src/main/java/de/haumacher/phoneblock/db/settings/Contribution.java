package de.haumacher.phoneblock.db.settings;

public class Contribution extends de.haumacher.msgbuf.data.AbstractDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.Contribution} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.Contribution create() {
		return new de.haumacher.phoneblock.db.settings.Contribution();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.Contribution} type in JSON format. */
	public static final String CONTRIBUTION__TYPE = "Contribution";

	/** @see #getId() */
	private static final String ID__PROP = "id";

	/** @see #getUserId() */
	private static final String USER_ID__PROP = "userId";

	/** @see #getSender() */
	private static final String SENDER__PROP = "sender";

	/** @see #getTx() */
	private static final String TX__PROP = "tx";

	/** @see #getAmount() */
	private static final String AMOUNT__PROP = "amount";

	/** @see #getMessage() */
	private static final String MESSAGE__PROP = "message";

	/** @see #getReceived() */
	private static final String RECEIVED__PROP = "received";

	/** @see #isAcknowledged() */
	private static final String ACKNOWLEDGED__PROP = "acknowledged";

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
		_acknowledged = value;
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

}
