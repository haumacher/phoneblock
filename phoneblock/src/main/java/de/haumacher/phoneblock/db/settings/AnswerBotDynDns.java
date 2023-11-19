package de.haumacher.phoneblock.db.settings;

/**
 * Configuration options and state of an answer bot.
 */
public class AnswerBotDynDns extends AnswerBotSetting {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.settings.AnswerBotDynDns} instance.
	 */
	public static de.haumacher.phoneblock.db.settings.AnswerBotDynDns create() {
		return new de.haumacher.phoneblock.db.settings.AnswerBotDynDns();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.AnswerBotDynDns} type in JSON format. */
	public static final String ANSWER_BOT_DYN_DNS__TYPE = "AnswerBotDynDns";

	/** @see #getDyndnsUser() */
	public static final String DYNDNS_USER__PROP = "dyndnsUser";

	/** @see #getDynDnsPasswd() */
	public static final String DYN_DNS_PASSWD__PROP = "dynDnsPasswd";

	/** @see #getIpv4() */
	public static final String IPV_4__PROP = "ipv4";

	/** @see #getIpv6() */
	public static final String IPV_6__PROP = "ipv6";

	/** Identifier for the {@link de.haumacher.phoneblock.db.settings.AnswerBotDynDns} type in binary format. */
	static final int ANSWER_BOT_DYN_DNS__TYPE_ID = 1;

	/** Identifier for the property {@link #getDyndnsUser()} in binary format. */
	static final int DYNDNS_USER__ID = 4;

	/** Identifier for the property {@link #getDynDnsPasswd()} in binary format. */
	static final int DYN_DNS_PASSWD__ID = 5;

	/** Identifier for the property {@link #getIpv4()} in binary format. */
	static final int IPV_4__ID = 6;

	/** Identifier for the property {@link #getIpv6()} in binary format. */
	static final int IPV_6__ID = 7;

	private String _dyndnsUser = "";

	private String _dynDnsPasswd = "";

	private String _ipv4 = "";

	private String _ipv6 = "";

	/**
	 * Creates a {@link AnswerBotDynDns} instance.
	 *
	 * @see de.haumacher.phoneblock.db.settings.AnswerBotDynDns#create()
	 */
	protected AnswerBotDynDns() {
		super();
	}

	@Override
	public TypeKind kind() {
		return TypeKind.ANSWER_BOT_DYN_DNS;
	}

	/**
	 * The user name for DynDNS update and SIP registration.
	 */
	public final String getDyndnsUser() {
		return _dyndnsUser;
	}

	/**
	 * @see #getDyndnsUser()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotDynDns setDyndnsUser(String value) {
		internalSetDyndnsUser(value);
		return this;
	}

	/** Internal setter for {@link #getDyndnsUser()} without chain call utility. */
	protected final void internalSetDyndnsUser(String value) {
		_listener.beforeSet(this, DYNDNS_USER__PROP, value);
		_dyndnsUser = value;
	}

	/**
	 * Password hash for DynDNS update.
	 */
	public final String getDynDnsPasswd() {
		return _dynDnsPasswd;
	}

	/**
	 * @see #getDynDnsPasswd()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotDynDns setDynDnsPasswd(String value) {
		internalSetDynDnsPasswd(value);
		return this;
	}

	/** Internal setter for {@link #getDynDnsPasswd()} without chain call utility. */
	protected final void internalSetDynDnsPasswd(String value) {
		_listener.beforeSet(this, DYN_DNS_PASSWD__PROP, value);
		_dynDnsPasswd = value;
	}

	/**
	 * IPv4 address transmitted during the last DynDNS update.
	 */
	public final String getIpv4() {
		return _ipv4;
	}

	/**
	 * @see #getIpv4()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotDynDns setIpv4(String value) {
		internalSetIpv4(value);
		return this;
	}

	/** Internal setter for {@link #getIpv4()} without chain call utility. */
	protected final void internalSetIpv4(String value) {
		_listener.beforeSet(this, IPV_4__PROP, value);
		_ipv4 = value;
	}

	/**
	 * IPv6 address transmitted during the last DynDNS update.
	 */
	public final String getIpv6() {
		return _ipv6;
	}

	/**
	 * @see #getIpv6()
	 */
	public de.haumacher.phoneblock.db.settings.AnswerBotDynDns setIpv6(String value) {
		internalSetIpv6(value);
		return this;
	}

	/** Internal setter for {@link #getIpv6()} without chain call utility. */
	protected final void internalSetIpv6(String value) {
		_listener.beforeSet(this, IPV_6__PROP, value);
		_ipv6 = value;
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotDynDns setUserId(long value) {
		internalSetUserId(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotDynDns setCreated(long value) {
		internalSetCreated(value);
		return this;
	}

	@Override
	public de.haumacher.phoneblock.db.settings.AnswerBotDynDns setUpdated(long value) {
		internalSetUpdated(value);
		return this;
	}

	@Override
	public String jsonType() {
		return ANSWER_BOT_DYN_DNS__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			DYNDNS_USER__PROP, 
			DYN_DNS_PASSWD__PROP, 
			IPV_4__PROP, 
			IPV_6__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case DYNDNS_USER__PROP: return getDyndnsUser();
			case DYN_DNS_PASSWD__PROP: return getDynDnsPasswd();
			case IPV_4__PROP: return getIpv4();
			case IPV_6__PROP: return getIpv6();
			default: return super.get(field);
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case DYNDNS_USER__PROP: internalSetDyndnsUser((String) value); break;
			case DYN_DNS_PASSWD__PROP: internalSetDynDnsPasswd((String) value); break;
			case IPV_4__PROP: internalSetIpv4((String) value); break;
			case IPV_6__PROP: internalSetIpv6((String) value); break;
			default: super.set(field, value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotDynDns readAnswerBotDynDns(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.AnswerBotDynDns result = new de.haumacher.phoneblock.db.settings.AnswerBotDynDns();
		result.readContent(in);
		return result;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.json.JsonWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(DYNDNS_USER__PROP);
		out.value(getDyndnsUser());
		out.name(DYN_DNS_PASSWD__PROP);
		out.value(getDynDnsPasswd());
		out.name(IPV_4__PROP);
		out.value(getIpv4());
		out.name(IPV_6__PROP);
		out.value(getIpv6());
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case DYNDNS_USER__PROP: setDyndnsUser(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case DYN_DNS_PASSWD__PROP: setDynDnsPasswd(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case IPV_4__PROP: setIpv4(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case IPV_6__PROP: setIpv6(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			default: super.readField(in, field);
		}
	}

	/** The binary identifier for this concrete type in the polymorphic {@link de.haumacher.phoneblock.db.settings.AnswerBotDynDns} hierarchy. */
	public int typeId() {
		return ANSWER_BOT_DYN_DNS__TYPE_ID;
	}

	@Override
	protected void writeFields(de.haumacher.msgbuf.binary.DataWriter out) throws java.io.IOException {
		super.writeFields(out);
		out.name(DYNDNS_USER__ID);
		out.value(getDyndnsUser());
		out.name(DYN_DNS_PASSWD__ID);
		out.value(getDynDnsPasswd());
		out.name(IPV_4__ID);
		out.value(getIpv4());
		out.name(IPV_6__ID);
		out.value(getIpv6());
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotDynDns readAnswerBotDynDns(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		in.beginObject();
		de.haumacher.phoneblock.db.settings.AnswerBotDynDns result = de.haumacher.phoneblock.db.settings.AnswerBotDynDns.readAnswerBotDynDns_Content(in);
		in.endObject();
		return result;
	}

	/** Helper for creating an object of type {@link de.haumacher.phoneblock.db.settings.AnswerBotDynDns} from a polymorphic composition. */
	public static de.haumacher.phoneblock.db.settings.AnswerBotDynDns readAnswerBotDynDns_Content(de.haumacher.msgbuf.binary.DataReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.settings.AnswerBotDynDns result = new AnswerBotDynDns();
		result.readContent(in);
		return result;
	}

	@Override
	protected void readField(de.haumacher.msgbuf.binary.DataReader in, int field) throws java.io.IOException {
		switch (field) {
			case DYNDNS_USER__ID: setDyndnsUser(in.nextString()); break;
			case DYN_DNS_PASSWD__ID: setDynDnsPasswd(in.nextString()); break;
			case IPV_4__ID: setIpv4(in.nextString()); break;
			case IPV_6__ID: setIpv6(in.nextString()); break;
			default: super.readField(in, field);
		}
	}

	@Override
	public <R,A,E extends Throwable> R visit(de.haumacher.phoneblock.db.settings.AnswerBotSetting.Visitor<R,A,E> v, A arg) throws E {
		return v.visit(this, arg);
	}

}
