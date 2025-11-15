package de.haumacher.phoneblock.ab.proto;

/**
 * Information of a single answer bot.
 */
public class AnswerbotInfo extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.data.ReflectiveDataObject {

	/**
	 * Creates a {@link de.haumacher.phoneblock.ab.proto.AnswerbotInfo} instance.
	 */
	public static de.haumacher.phoneblock.ab.proto.AnswerbotInfo create() {
		return new de.haumacher.phoneblock.ab.proto.AnswerbotInfo();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.ab.proto.AnswerbotInfo} type in JSON format. */
	public static final String ANSWERBOT_INFO__TYPE = "AnswerbotInfo";

	/** @see #getId() */
	public static final String ID__PROP = "id";

	/** @see #getUserId() */
	public static final String USER_ID__PROP = "userId";

	/** @see #isEnabled() */
	public static final String ENABLED__PROP = "enabled";

	/** @see #isPreferIPv4() */
	public static final String PREFER_IPV_4__PROP = "preferIPv4";

	/** @see #getMinVotes() */
	public static final String MIN_VOTES__PROP = "minVotes";

	/** @see #isWildcards() */
	public static final String WILDCARDS__PROP = "wildcards";

	/** @see #isRegistered() */
	public static final String REGISTERED__PROP = "registered";

	/** @see #getRegisterMsg() */
	public static final String REGISTER_MSG__PROP = "registerMsg";

	/** @see #getNewCalls() */
	public static final String NEW_CALLS__PROP = "newCalls";

	/** @see #getCallsAccepted() */
	public static final String CALLS_ACCEPTED__PROP = "callsAccepted";

	/** @see #getTalkTime() */
	public static final String TALK_TIME__PROP = "talkTime";

	/** @see #getRegistrar() */
	public static final String REGISTRAR__PROP = "registrar";

	/** @see #getRealm() */
	public static final String REALM__PROP = "realm";

	/** @see #getUserName() */
	public static final String USER_NAME__PROP = "userName";

	/** @see #getPassword() */
	public static final String PASSWORD__PROP = "password";

	/** @see #getHost() */
	public static final String HOST__PROP = "host";

	/** @see #getIp4() */
	public static final String IP_4__PROP = "ip4";

	/** @see #getIp6() */
	public static final String IP_6__PROP = "ip6";

	/** @see #getDyndnsUser() */
	public static final String DYNDNS_USER__PROP = "dyndnsUser";

	/** @see #getDyndnsPassword() */
	public static final String DYNDNS_PASSWORD__PROP = "dyndnsPassword";

	/** @see #getRetentionPeriod() */
	public static final String RETENTION_PERIOD__PROP = "retentionPeriod";

	private long _id = 0L;

	private long _userId = 0L;

	private boolean _enabled = false;

	private boolean _preferIPv4 = false;

	private int _minVotes = 0;

	private boolean _wildcards = false;

	private boolean _registered = false;

	private String _registerMsg = null;

	private int _newCalls = 0;

	private int _callsAccepted = 0;

	private long _talkTime = 0L;

	private String _registrar = "";

	private String _realm = "";

	private String _userName = "";

	private String _password = "";

	private String _host = null;

	private String _ip4 = null;

	private String _ip6 = null;

	private String _dyndnsUser = null;

	private String _dyndnsPassword = null;

	private de.haumacher.phoneblock.ab.proto.RetentionPeriod _retentionPeriod = de.haumacher.phoneblock.ab.proto.RetentionPeriod.NEVER;

	/**
	 * Creates a {@link AnswerbotInfo} instance.
	 *
	 * @see de.haumacher.phoneblock.ab.proto.AnswerbotInfo#create()
	 */
	protected AnswerbotInfo() {
		super();
	}

	/**
	 * The primary key identifier of this bot.
	 */
	public final long getId() {
		return _id;
	}

	/**
	 * @see #getId()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setId(long value) {
		internalSetId(value);
		return this;
	}

	/** Internal setter for {@link #getId()} without chain call utility. */
	protected final void internalSetId(long value) {
		_id = value;
	}

	/**
	 * The ID of the owning user.
	 */
	public final long getUserId() {
		return _userId;
	}

	/**
	 * @see #getUserId()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setUserId(long value) {
		internalSetUserId(value);
		return this;
	}

	/** Internal setter for {@link #getUserId()} without chain call utility. */
	protected final void internalSetUserId(long value) {
		_userId = value;
	}

	/**
	 * Whether the bot is enabled (registration is active).
	 */
	public final boolean isEnabled() {
		return _enabled;
	}

	/**
	 * @see #isEnabled()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setEnabled(boolean value) {
		internalSetEnabled(value);
		return this;
	}

	/** Internal setter for {@link #isEnabled()} without chain call utility. */
	protected final void internalSetEnabled(boolean value) {
		_enabled = value;
	}

	/**
	 * Whether to limit communication to IPv4.
	 */
	public final boolean isPreferIPv4() {
		return _preferIPv4;
	}

	/**
	 * @see #isPreferIPv4()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setPreferIPv4(boolean value) {
		internalSetPreferIPv4(value);
		return this;
	}

	/** Internal setter for {@link #isPreferIPv4()} without chain call utility. */
	protected final void internalSetPreferIPv4(boolean value) {
		_preferIPv4 = value;
	}

	/**
	 * The minimum PhoneBlock votes to consider a call as SPAM and accept it.
	 */
	public final int getMinVotes() {
		return _minVotes;
	}

	/**
	 * @see #getMinVotes()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setMinVotes(int value) {
		internalSetMinVotes(value);
		return this;
	}

	/** Internal setter for {@link #getMinVotes()} without chain call utility. */
	protected final void internalSetMinVotes(int value) {
		_minVotes = value;
	}

	/**
	 * Whether to block whole number ranges, when a great density of nearby SPAM numbers is detected.
	 */
	public final boolean isWildcards() {
		return _wildcards;
	}

	/**
	 * @see #isWildcards()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setWildcards(boolean value) {
		internalSetWildcards(value);
		return this;
	}

	/** Internal setter for {@link #isWildcards()} without chain call utility. */
	protected final void internalSetWildcards(boolean value) {
		_wildcards = value;
	}

	/**
	 * Whether the bot has sucessfully registered (can accept calls).
	 */
	public final boolean isRegistered() {
		return _registered;
	}

	/**
	 * @see #isRegistered()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setRegistered(boolean value) {
		internalSetRegistered(value);
		return this;
	}

	/** Internal setter for {@link #isRegistered()} without chain call utility. */
	protected final void internalSetRegistered(boolean value) {
		_registered = value;
	}

	/**
	 * The message received during the last registration attempt.
	 */
	public final String getRegisterMsg() {
		return _registerMsg;
	}

	/**
	 * @see #getRegisterMsg()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setRegisterMsg(String value) {
		internalSetRegisterMsg(value);
		return this;
	}

	/** Internal setter for {@link #getRegisterMsg()} without chain call utility. */
	protected final void internalSetRegisterMsg(String value) {
		_registerMsg = value;
	}

	/**
	 * Checks, whether {@link #getRegisterMsg()} has a value.
	 */
	public final boolean hasRegisterMsg() {
		return _registerMsg != null;
	}

	/**
	 * Number of new calls (reset when clearing the call list).
	 */
	public final int getNewCalls() {
		return _newCalls;
	}

	/**
	 * @see #getNewCalls()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setNewCalls(int value) {
		internalSetNewCalls(value);
		return this;
	}

	/** Internal setter for {@link #getNewCalls()} without chain call utility. */
	protected final void internalSetNewCalls(int value) {
		_newCalls = value;
	}

	/**
	 * The total number of calls accepted by this bot so far.
	 */
	public final int getCallsAccepted() {
		return _callsAccepted;
	}

	/**
	 * @see #getCallsAccepted()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setCallsAccepted(int value) {
		internalSetCallsAccepted(value);
		return this;
	}

	/** Internal setter for {@link #getCallsAccepted()} without chain call utility. */
	protected final void internalSetCallsAccepted(int value) {
		_callsAccepted = value;
	}

	/**
	 * The total time in milliseconds taked to SPAM customers.
	 */
	public final long getTalkTime() {
		return _talkTime;
	}

	/**
	 * @see #getTalkTime()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setTalkTime(long value) {
		internalSetTalkTime(value);
		return this;
	}

	/** Internal setter for {@link #getTalkTime()} without chain call utility. */
	protected final void internalSetTalkTime(long value) {
		_talkTime = value;
	}

	/**
	 * The name of the box to register at.
	 */
	public final String getRegistrar() {
		return _registrar;
	}

	/**
	 * @see #getRegistrar()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setRegistrar(String value) {
		internalSetRegistrar(value);
		return this;
	}

	/** Internal setter for {@link #getRegistrar()} without chain call utility. */
	protected final void internalSetRegistrar(String value) {
		_registrar = value;
	}

	/**
	 * The authentication realm expected for registration.
	 */
	public final String getRealm() {
		return _realm;
	}

	/**
	 * @see #getRealm()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setRealm(String value) {
		internalSetRealm(value);
		return this;
	}

	/** Internal setter for {@link #getRealm()} without chain call utility. */
	protected final void internalSetRealm(String value) {
		_realm = value;
	}

	/**
	 * The user name used for SIP registration.
	 */
	public final String getUserName() {
		return _userName;
	}

	/**
	 * @see #getUserName()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setUserName(String value) {
		internalSetUserName(value);
		return this;
	}

	/** Internal setter for {@link #getUserName()} without chain call utility. */
	protected final void internalSetUserName(String value) {
		_userName = value;
	}

	/**
	 * The password for SIP registration.
	 */
	public final String getPassword() {
		return _password;
	}

	/**
	 * @see #getPassword()
	 */
	public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setPassword(String value) {
		internalSetPassword(value);
		return this;
	}

	/** Internal setter for {@link #getPassword()} without chain call utility. */
	protected final void internalSetPassword(String value) {
		_password = value;
	}

	/**
	 * The host name of the box to register at (only set, if a third-party DynDNS service is used.
		 */
		public final String getHost() {
			return _host;
		}

		/**
		 * @see #getHost()
		 */
		public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setHost(String value) {
			internalSetHost(value);
			return this;
		}

		/** Internal setter for {@link #getHost()} without chain call utility. */
		protected final void internalSetHost(String value) {
			_host = value;
		}

		/**
		 * Checks, whether {@link #getHost()} has a value.
		 */
		public final boolean hasHost() {
			return _host != null;
		}

		/**
		 * The IPv4 address of the box to register at (only filled, if internal DynDNS is set up and succeeded).
		 */
		public final String getIp4() {
			return _ip4;
		}

		/**
		 * @see #getIp4()
		 */
		public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setIp4(String value) {
			internalSetIp4(value);
			return this;
		}

		/** Internal setter for {@link #getIp4()} without chain call utility. */
		protected final void internalSetIp4(String value) {
			_ip4 = value;
		}

		/**
		 * Checks, whether {@link #getIp4()} has a value.
		 */
		public final boolean hasIp4() {
			return _ip4 != null;
		}

		/**
		 * The IPv6 address of the box to register at (only filled, if internal DynDNS is set up and succeeded).
		 */
		public final String getIp6() {
			return _ip6;
		}

		/**
		 * @see #getIp6()
		 */
		public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setIp6(String value) {
			internalSetIp6(value);
			return this;
		}

		/** Internal setter for {@link #getIp6()} without chain call utility. */
		protected final void internalSetIp6(String value) {
			_ip6 = value;
		}

		/**
		 * Checks, whether {@link #getIp6()} has a value.
		 */
		public final boolean hasIp6() {
			return _ip6 != null;
		}

		/**
		 * The user name for DynDNS registration of the box (only filled, if internal DynDNS is set up).
		 */
		public final String getDyndnsUser() {
			return _dyndnsUser;
		}

		/**
		 * @see #getDyndnsUser()
		 */
		public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setDyndnsUser(String value) {
			internalSetDyndnsUser(value);
			return this;
		}

		/** Internal setter for {@link #getDyndnsUser()} without chain call utility. */
		protected final void internalSetDyndnsUser(String value) {
			_dyndnsUser = value;
		}

		/**
		 * Checks, whether {@link #getDyndnsUser()} has a value.
		 */
		public final boolean hasDyndnsUser() {
			return _dyndnsUser != null;
		}

		/**
		 * The password for DynDNS registration of the box.
		 */
		public final String getDyndnsPassword() {
			return _dyndnsPassword;
		}

		/**
		 * @see #getDyndnsPassword()
		 */
		public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setDyndnsPassword(String value) {
			internalSetDyndnsPassword(value);
			return this;
		}

		/** Internal setter for {@link #getDyndnsPassword()} without chain call utility. */
		protected final void internalSetDyndnsPassword(String value) {
			_dyndnsPassword = value;
		}

		/**
		 * Checks, whether {@link #getDyndnsPassword()} has a value.
		 */
		public final boolean hasDyndnsPassword() {
			return _dyndnsPassword != null;
		}

		/**
		 * The retention period for automatic call cleanup (NEVER, WEEK, MONTH, QUARTER, YEAR).
		 */
		public final de.haumacher.phoneblock.ab.proto.RetentionPeriod getRetentionPeriod() {
			return _retentionPeriod;
		}

		/**
		 * @see #getRetentionPeriod()
		 */
		public de.haumacher.phoneblock.ab.proto.AnswerbotInfo setRetentionPeriod(de.haumacher.phoneblock.ab.proto.RetentionPeriod value) {
			internalSetRetentionPeriod(value);
			return this;
		}

		/** Internal setter for {@link #getRetentionPeriod()} without chain call utility. */
		protected final void internalSetRetentionPeriod(de.haumacher.phoneblock.ab.proto.RetentionPeriod value) {
			if (value == null) throw new IllegalArgumentException("Property 'retentionPeriod' cannot be null.");
			_retentionPeriod = value;
		}

		@Override
		public String jsonType() {
			return ANSWERBOT_INFO__TYPE;
		}

		private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
			java.util.Arrays.asList(
				ID__PROP, 
				USER_ID__PROP, 
				ENABLED__PROP, 
				PREFER_IPV_4__PROP, 
				MIN_VOTES__PROP, 
				WILDCARDS__PROP, 
				REGISTERED__PROP, 
				REGISTER_MSG__PROP, 
				NEW_CALLS__PROP, 
				CALLS_ACCEPTED__PROP, 
				TALK_TIME__PROP, 
				REGISTRAR__PROP, 
				REALM__PROP, 
				USER_NAME__PROP, 
				PASSWORD__PROP, 
				HOST__PROP, 
				IP_4__PROP, 
				IP_6__PROP, 
				DYNDNS_USER__PROP, 
				DYNDNS_PASSWORD__PROP, 
				RETENTION_PERIOD__PROP));

		@Override
		public java.util.List<String> properties() {
			return PROPERTIES;
		}

		@Override
		public Object get(String field) {
			switch (field) {
				case ID__PROP: return getId();
				case USER_ID__PROP: return getUserId();
				case ENABLED__PROP: return isEnabled();
				case PREFER_IPV_4__PROP: return isPreferIPv4();
				case MIN_VOTES__PROP: return getMinVotes();
				case WILDCARDS__PROP: return isWildcards();
				case REGISTERED__PROP: return isRegistered();
				case REGISTER_MSG__PROP: return getRegisterMsg();
				case NEW_CALLS__PROP: return getNewCalls();
				case CALLS_ACCEPTED__PROP: return getCallsAccepted();
				case TALK_TIME__PROP: return getTalkTime();
				case REGISTRAR__PROP: return getRegistrar();
				case REALM__PROP: return getRealm();
				case USER_NAME__PROP: return getUserName();
				case PASSWORD__PROP: return getPassword();
				case HOST__PROP: return getHost();
				case IP_4__PROP: return getIp4();
				case IP_6__PROP: return getIp6();
				case DYNDNS_USER__PROP: return getDyndnsUser();
				case DYNDNS_PASSWORD__PROP: return getDyndnsPassword();
				case RETENTION_PERIOD__PROP: return getRetentionPeriod();
				default: return null;
			}
		}

		@Override
		public void set(String field, Object value) {
			switch (field) {
				case ID__PROP: internalSetId((long) value); break;
				case USER_ID__PROP: internalSetUserId((long) value); break;
				case ENABLED__PROP: internalSetEnabled((boolean) value); break;
				case PREFER_IPV_4__PROP: internalSetPreferIPv4((boolean) value); break;
				case MIN_VOTES__PROP: internalSetMinVotes((int) value); break;
				case WILDCARDS__PROP: internalSetWildcards((boolean) value); break;
				case REGISTERED__PROP: internalSetRegistered((boolean) value); break;
				case REGISTER_MSG__PROP: internalSetRegisterMsg((String) value); break;
				case NEW_CALLS__PROP: internalSetNewCalls((int) value); break;
				case CALLS_ACCEPTED__PROP: internalSetCallsAccepted((int) value); break;
				case TALK_TIME__PROP: internalSetTalkTime((long) value); break;
				case REGISTRAR__PROP: internalSetRegistrar((String) value); break;
				case REALM__PROP: internalSetRealm((String) value); break;
				case USER_NAME__PROP: internalSetUserName((String) value); break;
				case PASSWORD__PROP: internalSetPassword((String) value); break;
				case HOST__PROP: internalSetHost((String) value); break;
				case IP_4__PROP: internalSetIp4((String) value); break;
				case IP_6__PROP: internalSetIp6((String) value); break;
				case DYNDNS_USER__PROP: internalSetDyndnsUser((String) value); break;
				case DYNDNS_PASSWORD__PROP: internalSetDyndnsPassword((String) value); break;
				case RETENTION_PERIOD__PROP: internalSetRetentionPeriod((de.haumacher.phoneblock.ab.proto.RetentionPeriod) value); break;
			}
		}

		/** Reads a new instance from the given reader. */
		public static de.haumacher.phoneblock.ab.proto.AnswerbotInfo readAnswerbotInfo(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
			de.haumacher.phoneblock.ab.proto.AnswerbotInfo result = new de.haumacher.phoneblock.ab.proto.AnswerbotInfo();
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
			out.name(USER_ID__PROP);
			out.value(getUserId());
			out.name(ENABLED__PROP);
			out.value(isEnabled());
			out.name(PREFER_IPV_4__PROP);
			out.value(isPreferIPv4());
			out.name(MIN_VOTES__PROP);
			out.value(getMinVotes());
			out.name(WILDCARDS__PROP);
			out.value(isWildcards());
			out.name(REGISTERED__PROP);
			out.value(isRegistered());
			if (hasRegisterMsg()) {
				out.name(REGISTER_MSG__PROP);
				out.value(getRegisterMsg());
			}
			out.name(NEW_CALLS__PROP);
			out.value(getNewCalls());
			out.name(CALLS_ACCEPTED__PROP);
			out.value(getCallsAccepted());
			out.name(TALK_TIME__PROP);
			out.value(getTalkTime());
			out.name(REGISTRAR__PROP);
			out.value(getRegistrar());
			out.name(REALM__PROP);
			out.value(getRealm());
			out.name(USER_NAME__PROP);
			out.value(getUserName());
			out.name(PASSWORD__PROP);
			out.value(getPassword());
			if (hasHost()) {
				out.name(HOST__PROP);
				out.value(getHost());
			}
			if (hasIp4()) {
				out.name(IP_4__PROP);
				out.value(getIp4());
			}
			if (hasIp6()) {
				out.name(IP_6__PROP);
				out.value(getIp6());
			}
			if (hasDyndnsUser()) {
				out.name(DYNDNS_USER__PROP);
				out.value(getDyndnsUser());
			}
			if (hasDyndnsPassword()) {
				out.name(DYNDNS_PASSWORD__PROP);
				out.value(getDyndnsPassword());
			}
			out.name(RETENTION_PERIOD__PROP);
			getRetentionPeriod().writeTo(out);
		}

		@Override
		protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
			switch (field) {
				case ID__PROP: setId(in.nextLong()); break;
				case USER_ID__PROP: setUserId(in.nextLong()); break;
				case ENABLED__PROP: setEnabled(in.nextBoolean()); break;
				case PREFER_IPV_4__PROP: setPreferIPv4(in.nextBoolean()); break;
				case MIN_VOTES__PROP: setMinVotes(in.nextInt()); break;
				case WILDCARDS__PROP: setWildcards(in.nextBoolean()); break;
				case REGISTERED__PROP: setRegistered(in.nextBoolean()); break;
				case REGISTER_MSG__PROP: setRegisterMsg(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case NEW_CALLS__PROP: setNewCalls(in.nextInt()); break;
				case CALLS_ACCEPTED__PROP: setCallsAccepted(in.nextInt()); break;
				case TALK_TIME__PROP: setTalkTime(in.nextLong()); break;
				case REGISTRAR__PROP: setRegistrar(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case REALM__PROP: setRealm(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case USER_NAME__PROP: setUserName(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case PASSWORD__PROP: setPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case HOST__PROP: setHost(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case IP_4__PROP: setIp4(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case IP_6__PROP: setIp6(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case DYNDNS_USER__PROP: setDyndnsUser(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case DYNDNS_PASSWORD__PROP: setDyndnsPassword(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
				case RETENTION_PERIOD__PROP: setRetentionPeriod(de.haumacher.phoneblock.ab.proto.RetentionPeriod.readRetentionPeriod(in)); break;
				default: super.readField(in, field);
			}
		}

	}
