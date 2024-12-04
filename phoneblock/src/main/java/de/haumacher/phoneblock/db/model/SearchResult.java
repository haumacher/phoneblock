package de.haumacher.phoneblock.db.model;

public class SearchResult extends de.haumacher.msgbuf.data.AbstractDataObject implements de.haumacher.msgbuf.observer.Observable, de.haumacher.msgbuf.xml.XmlSerializable {

	/**
	 * Creates a {@link de.haumacher.phoneblock.db.model.SearchResult} instance.
	 */
	public static de.haumacher.phoneblock.db.model.SearchResult create() {
		return new de.haumacher.phoneblock.db.model.SearchResult();
	}

	/** Identifier for the {@link de.haumacher.phoneblock.db.model.SearchResult} type in JSON format. */
	public static final String SEARCH_RESULT__TYPE = "SearchResult";

	/** @see #getPhoneId() */
	public static final String PHONE_ID__PROP = "phoneId";

	/** @see #getNumber() */
	public static final String NUMBER__PROP = "number";

	/** @see #getComments() */
	public static final String COMMENTS__PROP = "comments";

	/** @see #getInfo() */
	public static final String INFO__PROP = "info";

	/** @see #getSearches() */
	public static final String SEARCHES__PROP = "searches";

	/** @see #getAiSummary() */
	public static final String AI_SUMMARY__PROP = "aiSummary";

	/** @see #getRelatedNumbers() */
	public static final String RELATED_NUMBERS__PROP = "relatedNumbers";

	/** @see #getPrev() */
	public static final String PREV__PROP = "prev";

	/** @see #getNext() */
	public static final String NEXT__PROP = "next";

	/** @see #getTopRating() */
	public static final String TOP_RATING__PROP = "topRating";

	/** @see #getRatings() */
	public static final String RATINGS__PROP = "ratings";

	private String _phoneId = "";

	private de.haumacher.phoneblock.db.model.PhoneNumer _number = null;

	private final java.util.List<de.haumacher.phoneblock.db.model.UserComment> _comments = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, de.haumacher.phoneblock.db.model.UserComment element) {
			_listener.beforeAdd(SearchResult.this, COMMENTS__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, de.haumacher.phoneblock.db.model.UserComment element) {
			_listener.afterRemove(SearchResult.this, COMMENTS__PROP, index, element);
		}
	};

	private de.haumacher.phoneblock.db.model.PhoneInfo _info = null;

	private final java.util.List<Integer> _searches = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, Integer element) {
			_listener.beforeAdd(SearchResult.this, SEARCHES__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, Integer element) {
			_listener.afterRemove(SearchResult.this, SEARCHES__PROP, index, element);
		}
	};

	private String _aiSummary = "";

	private final java.util.List<String> _relatedNumbers = new de.haumacher.msgbuf.util.ReferenceList<>() {
		@Override
		protected void beforeAdd(int index, String element) {
			_listener.beforeAdd(SearchResult.this, RELATED_NUMBERS__PROP, index, element);
		}

		@Override
		protected void afterRemove(int index, String element) {
			_listener.afterRemove(SearchResult.this, RELATED_NUMBERS__PROP, index, element);
		}
	};

	private String _prev = "";

	private String _next = "";

	private de.haumacher.phoneblock.db.model.Rating _topRating = de.haumacher.phoneblock.db.model.Rating.A_LEGITIMATE;

	private final java.util.Map<de.haumacher.phoneblock.db.model.Rating, Integer> _ratings = new de.haumacher.msgbuf.util.ReferenceMap<>() {
		@Override
		protected void beforeAdd(de.haumacher.phoneblock.db.model.Rating index, Integer element) {
			_listener.beforeAdd(SearchResult.this, RATINGS__PROP, index, element);
		}

		@Override
		protected void afterRemove(de.haumacher.phoneblock.db.model.Rating index, Integer element) {
			_listener.afterRemove(SearchResult.this, RATINGS__PROP, index, element);
		}
	};

	/**
	 * Creates a {@link SearchResult} instance.
	 *
	 * @see de.haumacher.phoneblock.db.model.SearchResult#create()
	 */
	protected SearchResult() {
		super();
	}

	public final String getPhoneId() {
		return _phoneId;
	}

	/**
	 * @see #getPhoneId()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setPhoneId(String value) {
		internalSetPhoneId(value);
		return this;
	}

	/** Internal setter for {@link #getPhoneId()} without chain call utility. */
	protected final void internalSetPhoneId(String value) {
		_listener.beforeSet(this, PHONE_ID__PROP, value);
		_phoneId = value;
	}

	public final de.haumacher.phoneblock.db.model.PhoneNumer getNumber() {
		return _number;
	}

	/**
	 * @see #getNumber()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setNumber(de.haumacher.phoneblock.db.model.PhoneNumer value) {
		internalSetNumber(value);
		return this;
	}

	/** Internal setter for {@link #getNumber()} without chain call utility. */
	protected final void internalSetNumber(de.haumacher.phoneblock.db.model.PhoneNumer value) {
		_listener.beforeSet(this, NUMBER__PROP, value);
		_number = value;
	}

	/**
	 * Checks, whether {@link #getNumber()} has a value.
	 */
	public final boolean hasNumber() {
		return _number != null;
	}

	public final java.util.List<de.haumacher.phoneblock.db.model.UserComment> getComments() {
		return _comments;
	}

	/**
	 * @see #getComments()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setComments(java.util.List<? extends de.haumacher.phoneblock.db.model.UserComment> value) {
		internalSetComments(value);
		return this;
	}

	/** Internal setter for {@link #getComments()} without chain call utility. */
	protected final void internalSetComments(java.util.List<? extends de.haumacher.phoneblock.db.model.UserComment> value) {
		if (value == null) throw new IllegalArgumentException("Property 'comments' cannot be null.");
		_comments.clear();
		_comments.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getComments()} list.
	 */
	public de.haumacher.phoneblock.db.model.SearchResult addComment(de.haumacher.phoneblock.db.model.UserComment value) {
		internalAddComment(value);
		return this;
	}

	/** Implementation of {@link #addComment(de.haumacher.phoneblock.db.model.UserComment)} without chain call utility. */
	protected final void internalAddComment(de.haumacher.phoneblock.db.model.UserComment value) {
		_comments.add(value);
	}

	/**
	 * Removes a value from the {@link #getComments()} list.
	 */
	public final void removeComment(de.haumacher.phoneblock.db.model.UserComment value) {
		_comments.remove(value);
	}

	public final de.haumacher.phoneblock.db.model.PhoneInfo getInfo() {
		return _info;
	}

	/**
	 * @see #getInfo()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setInfo(de.haumacher.phoneblock.db.model.PhoneInfo value) {
		internalSetInfo(value);
		return this;
	}

	/** Internal setter for {@link #getInfo()} without chain call utility. */
	protected final void internalSetInfo(de.haumacher.phoneblock.db.model.PhoneInfo value) {
		_listener.beforeSet(this, INFO__PROP, value);
		_info = value;
	}

	/**
	 * Checks, whether {@link #getInfo()} has a value.
	 */
	public final boolean hasInfo() {
		return _info != null;
	}

	public final java.util.List<Integer> getSearches() {
		return _searches;
	}

	/**
	 * @see #getSearches()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setSearches(java.util.List<? extends Integer> value) {
		internalSetSearches(value);
		return this;
	}

	/** Internal setter for {@link #getSearches()} without chain call utility. */
	protected final void internalSetSearches(java.util.List<? extends Integer> value) {
		_searches.clear();
		_searches.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getSearches()} list.
	 */
	public de.haumacher.phoneblock.db.model.SearchResult addSearche(int value) {
		internalAddSearche(value);
		return this;
	}

	/** Implementation of {@link #addSearche(int)} without chain call utility. */
	protected final void internalAddSearche(int value) {
		_searches.add(value);
	}

	/**
	 * Removes a value from the {@link #getSearches()} list.
	 */
	public final void removeSearche(int value) {
		_searches.remove(value);
	}

	public final String getAiSummary() {
		return _aiSummary;
	}

	/**
	 * @see #getAiSummary()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setAiSummary(String value) {
		internalSetAiSummary(value);
		return this;
	}

	/** Internal setter for {@link #getAiSummary()} without chain call utility. */
	protected final void internalSetAiSummary(String value) {
		_listener.beforeSet(this, AI_SUMMARY__PROP, value);
		_aiSummary = value;
	}

	public final java.util.List<String> getRelatedNumbers() {
		return _relatedNumbers;
	}

	/**
	 * @see #getRelatedNumbers()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setRelatedNumbers(java.util.List<? extends String> value) {
		internalSetRelatedNumbers(value);
		return this;
	}

	/** Internal setter for {@link #getRelatedNumbers()} without chain call utility. */
	protected final void internalSetRelatedNumbers(java.util.List<? extends String> value) {
		_relatedNumbers.clear();
		_relatedNumbers.addAll(value);
	}

	/**
	 * Adds a value to the {@link #getRelatedNumbers()} list.
	 */
	public de.haumacher.phoneblock.db.model.SearchResult addRelatedNumber(String value) {
		internalAddRelatedNumber(value);
		return this;
	}

	/** Implementation of {@link #addRelatedNumber(String)} without chain call utility. */
	protected final void internalAddRelatedNumber(String value) {
		_relatedNumbers.add(value);
	}

	/**
	 * Removes a value from the {@link #getRelatedNumbers()} list.
	 */
	public final void removeRelatedNumber(String value) {
		_relatedNumbers.remove(value);
	}

	public final String getPrev() {
		return _prev;
	}

	/**
	 * @see #getPrev()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setPrev(String value) {
		internalSetPrev(value);
		return this;
	}

	/** Internal setter for {@link #getPrev()} without chain call utility. */
	protected final void internalSetPrev(String value) {
		_listener.beforeSet(this, PREV__PROP, value);
		_prev = value;
	}

	public final String getNext() {
		return _next;
	}

	/**
	 * @see #getNext()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setNext(String value) {
		internalSetNext(value);
		return this;
	}

	/** Internal setter for {@link #getNext()} without chain call utility. */
	protected final void internalSetNext(String value) {
		_listener.beforeSet(this, NEXT__PROP, value);
		_next = value;
	}

	public final de.haumacher.phoneblock.db.model.Rating getTopRating() {
		return _topRating;
	}

	/**
	 * @see #getTopRating()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setTopRating(de.haumacher.phoneblock.db.model.Rating value) {
		internalSetTopRating(value);
		return this;
	}

	/** Internal setter for {@link #getTopRating()} without chain call utility. */
	protected final void internalSetTopRating(de.haumacher.phoneblock.db.model.Rating value) {
		if (value == null) throw new IllegalArgumentException("Property 'topRating' cannot be null.");
		_listener.beforeSet(this, TOP_RATING__PROP, value);
		_topRating = value;
	}

	public final java.util.Map<de.haumacher.phoneblock.db.model.Rating, Integer> getRatings() {
		return _ratings;
	}

	/**
	 * @see #getRatings()
	 */
	public de.haumacher.phoneblock.db.model.SearchResult setRatings(java.util.Map<de.haumacher.phoneblock.db.model.Rating, Integer> value) {
		internalSetRatings(value);
		return this;
	}

	/** Internal setter for {@link #getRatings()} without chain call utility. */
	protected final void internalSetRatings(java.util.Map<de.haumacher.phoneblock.db.model.Rating, Integer> value) {
		if (value == null) throw new IllegalArgumentException("Property 'ratings' cannot be null.");
		_ratings.clear();
		_ratings.putAll(value);
	}

	/**
	 * Adds a key value pair to the {@link #getRatings()} map.
	 */
	public de.haumacher.phoneblock.db.model.SearchResult putRating(de.haumacher.phoneblock.db.model.Rating key, int value) {
		internalPutRating(key, value);
		return this;
	}

	/** Implementation of {@link #putRating(de.haumacher.phoneblock.db.model.Rating, int)} without chain call utility. */
	protected final void  internalPutRating(de.haumacher.phoneblock.db.model.Rating key, int value) {
		if (_ratings.containsKey(key)) {
			throw new IllegalArgumentException("Property 'ratings' already contains a value for key '" + key + "'.");
		}
		_ratings.put(key, value);
	}

	/**
	 * Removes a key from the {@link #getRatings()} map.
	 */
	public final void removeRating(de.haumacher.phoneblock.db.model.Rating key) {
		_ratings.remove(key);
	}

	protected de.haumacher.msgbuf.observer.Listener _listener = de.haumacher.msgbuf.observer.Listener.NONE;

	@Override
	public de.haumacher.phoneblock.db.model.SearchResult registerListener(de.haumacher.msgbuf.observer.Listener l) {
		internalRegisterListener(l);
		return this;
	}

	protected final void internalRegisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.register(_listener, l);
	}

	@Override
	public de.haumacher.phoneblock.db.model.SearchResult unregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		internalUnregisterListener(l);
		return this;
	}

	protected final void internalUnregisterListener(de.haumacher.msgbuf.observer.Listener l) {
		_listener = de.haumacher.msgbuf.observer.Listener.unregister(_listener, l);
	}

	@Override
	public String jsonType() {
		return SEARCH_RESULT__TYPE;
	}

	private static java.util.List<String> PROPERTIES = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			PHONE_ID__PROP, 
			NUMBER__PROP, 
			COMMENTS__PROP, 
			INFO__PROP, 
			SEARCHES__PROP, 
			AI_SUMMARY__PROP, 
			RELATED_NUMBERS__PROP, 
			PREV__PROP, 
			NEXT__PROP, 
			TOP_RATING__PROP, 
			RATINGS__PROP));

	@Override
	public java.util.List<String> properties() {
		return PROPERTIES;
	}

	@Override
	public Object get(String field) {
		switch (field) {
			case PHONE_ID__PROP: return getPhoneId();
			case NUMBER__PROP: return getNumber();
			case COMMENTS__PROP: return getComments();
			case INFO__PROP: return getInfo();
			case SEARCHES__PROP: return getSearches();
			case AI_SUMMARY__PROP: return getAiSummary();
			case RELATED_NUMBERS__PROP: return getRelatedNumbers();
			case PREV__PROP: return getPrev();
			case NEXT__PROP: return getNext();
			case TOP_RATING__PROP: return getTopRating();
			case RATINGS__PROP: return getRatings();
			default: return null;
		}
	}

	@Override
	public void set(String field, Object value) {
		switch (field) {
			case PHONE_ID__PROP: internalSetPhoneId((String) value); break;
			case NUMBER__PROP: internalSetNumber((de.haumacher.phoneblock.db.model.PhoneNumer) value); break;
			case COMMENTS__PROP: internalSetComments(de.haumacher.msgbuf.util.Conversions.asList(de.haumacher.phoneblock.db.model.UserComment.class, value)); break;
			case INFO__PROP: internalSetInfo((de.haumacher.phoneblock.db.model.PhoneInfo) value); break;
			case SEARCHES__PROP: internalSetSearches(de.haumacher.msgbuf.util.Conversions.asList(Integer.class, value)); break;
			case AI_SUMMARY__PROP: internalSetAiSummary((String) value); break;
			case RELATED_NUMBERS__PROP: internalSetRelatedNumbers(de.haumacher.msgbuf.util.Conversions.asList(String.class, value)); break;
			case PREV__PROP: internalSetPrev((String) value); break;
			case NEXT__PROP: internalSetNext((String) value); break;
			case TOP_RATING__PROP: internalSetTopRating((de.haumacher.phoneblock.db.model.Rating) value); break;
			case RATINGS__PROP: internalSetRatings((java.util.Map<de.haumacher.phoneblock.db.model.Rating, Integer>) value); break;
		}
	}

	/** Reads a new instance from the given reader. */
	public static de.haumacher.phoneblock.db.model.SearchResult readSearchResult(de.haumacher.msgbuf.json.JsonReader in) throws java.io.IOException {
		de.haumacher.phoneblock.db.model.SearchResult result = new de.haumacher.phoneblock.db.model.SearchResult();
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
		out.name(PHONE_ID__PROP);
		out.value(getPhoneId());
		if (hasNumber()) {
			out.name(NUMBER__PROP);
			getNumber().writeTo(out);
		}
		out.name(COMMENTS__PROP);
		out.beginArray();
		for (de.haumacher.phoneblock.db.model.UserComment x : getComments()) {
			x.writeTo(out);
		}
		out.endArray();
		if (hasInfo()) {
			out.name(INFO__PROP);
			getInfo().writeContent(out);
		}
		out.name(SEARCHES__PROP);
		out.beginArray();
		for (int x : getSearches()) {
			out.value(x);
		}
		out.endArray();
		out.name(AI_SUMMARY__PROP);
		out.value(getAiSummary());
		out.name(RELATED_NUMBERS__PROP);
		out.beginArray();
		for (String x : getRelatedNumbers()) {
			out.value(x);
		}
		out.endArray();
		out.name(PREV__PROP);
		out.value(getPrev());
		out.name(NEXT__PROP);
		out.value(getNext());
		out.name(TOP_RATING__PROP);
		getTopRating().writeTo(out);
		out.name(RATINGS__PROP);
		out.beginArray();
		for (java.util.Map.Entry<de.haumacher.phoneblock.db.model.Rating,Integer> entry : getRatings().entrySet()) {
			out.beginObject();
			out.name("key");
			entry.getKey().writeTo(out);
			out.name("value");
			out.value(entry.getValue());
			out.endObject();
		}
		out.endArray();
	}

	@Override
	protected void readField(de.haumacher.msgbuf.json.JsonReader in, String field) throws java.io.IOException {
		switch (field) {
			case PHONE_ID__PROP: setPhoneId(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case NUMBER__PROP: setNumber(de.haumacher.phoneblock.db.model.PhoneNumer.readPhoneNumer(in)); break;
			case COMMENTS__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addComment(de.haumacher.phoneblock.db.model.UserComment.readUserComment(in));
				}
				in.endArray();
			}
			break;
			case INFO__PROP: setInfo(de.haumacher.phoneblock.db.model.PhoneInfo.readPhoneInfo(in)); break;
			case SEARCHES__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addSearche(in.nextInt());
				}
				in.endArray();
			}
			break;
			case AI_SUMMARY__PROP: setAiSummary(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case RELATED_NUMBERS__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					addRelatedNumber(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in));
				}
				in.endArray();
			}
			break;
			case PREV__PROP: setPrev(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case NEXT__PROP: setNext(de.haumacher.msgbuf.json.JsonUtil.nextStringOptional(in)); break;
			case TOP_RATING__PROP: setTopRating(de.haumacher.phoneblock.db.model.Rating.readRating(in)); break;
			case RATINGS__PROP: {
				in.beginArray();
				while (in.hasNext()) {
					in.beginObject();
					de.haumacher.phoneblock.db.model.Rating key = de.haumacher.phoneblock.db.model.Rating.A_LEGITIMATE;
					int value = 0;
					while (in.hasNext()) {
						switch (in.nextName()) {
							case "key": key = de.haumacher.phoneblock.db.model.Rating.readRating(in); break;
							case "value": value = in.nextInt(); break;
							default: in.skipValue(); break;
						}
					}
					putRating(key, value);
					in.endObject();
				}
				in.endArray();
				break;
			}
			default: super.readField(in, field);
		}
	}

	/** XML element name representing a {@link de.haumacher.phoneblock.db.model.SearchResult} type. */
	public static final String SEARCH_RESULT__XML_ELEMENT = "search-result";

	/** XML attribute or element name of a {@link #getPhoneId} property. */
	private static final String PHONE_ID__XML_ATTR = "phone-id";

	/** XML attribute or element name of a {@link #getNumber} property. */
	private static final String NUMBER__XML_ATTR = "number";

	/** XML attribute or element name of a {@link #getComments} property. */
	private static final String COMMENTS__XML_ATTR = "comments";

	/** XML attribute or element name of a {@link #getInfo} property. */
	private static final String INFO__XML_ATTR = "info";

	/** XML attribute or element name of a {@link #getSearches} property. */
	private static final String SEARCHES__XML_ATTR = "searches";

	/** XML attribute or element name of a {@link #getAiSummary} property. */
	private static final String AI_SUMMARY__XML_ATTR = "ai-summary";

	/** XML attribute or element name of a {@link #getRelatedNumbers} property. */
	private static final String RELATED_NUMBERS__XML_ATTR = "related-numbers";

	/** XML attribute or element name of a {@link #getPrev} property. */
	private static final String PREV__XML_ATTR = "prev";

	/** XML attribute or element name of a {@link #getNext} property. */
	private static final String NEXT__XML_ATTR = "next";

	/** XML attribute or element name of a {@link #getTopRating} property. */
	private static final String TOP_RATING__XML_ATTR = "top-rating";

	/** XML attribute or element name of a {@link #getRatings} property. */
	private static final String RATINGS__XML_ATTR = "ratings";

	@Override
	public String getXmlTagName() {
		return SEARCH_RESULT__XML_ELEMENT;
	}

	@Override
	public final void writeContent(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		writeAttributes(out);
		writeElements(out);
	}

	/** Serializes all fields that are written as XML attributes. */
	protected void writeAttributes(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		out.writeAttribute(PHONE_ID__XML_ATTR, getPhoneId());
		out.writeAttribute(SEARCHES__XML_ATTR, getSearches().stream().map(x -> Integer.toString(x)).collect(java.util.stream.Collectors.joining(", ")));
		out.writeAttribute(AI_SUMMARY__XML_ATTR, getAiSummary());
		out.writeAttribute(RELATED_NUMBERS__XML_ATTR, getRelatedNumbers().stream().map(x -> x).collect(java.util.stream.Collectors.joining(", ")));
		out.writeAttribute(PREV__XML_ATTR, getPrev());
		out.writeAttribute(NEXT__XML_ATTR, getNext());
		out.writeAttribute(TOP_RATING__XML_ATTR, getTopRating().protocolName());
	}

	/** Serializes all fields that are written as XML elements. */
	protected void writeElements(javax.xml.stream.XMLStreamWriter out) throws javax.xml.stream.XMLStreamException {
		if (hasNumber()) {
			out.writeStartElement(NUMBER__XML_ATTR);
			getNumber().writeContent(out);
			out.writeEndElement();
		}
		out.writeStartElement(COMMENTS__XML_ATTR);
		for (de.haumacher.phoneblock.db.model.UserComment element : getComments()) {
			element.writeTo(out);
		}
		out.writeEndElement();
		if (hasInfo()) {
			out.writeStartElement(INFO__XML_ATTR);
			getInfo().writeContent(out);
			out.writeEndElement();
		}
	}

	/** Creates a new {@link de.haumacher.phoneblock.db.model.SearchResult} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static SearchResult readSearchResult_XmlContent(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		SearchResult result = new SearchResult();
		result.readContentXml(in);
		return result;
	}

	/** Reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	protected final void readContentXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		for (int n = 0, cnt = in.getAttributeCount(); n < cnt; n++) {
			String name = in.getAttributeLocalName(n);
			String value = in.getAttributeValue(n);

			readFieldXmlAttribute(name, value);
		}
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}
			assert event == javax.xml.stream.XMLStreamConstants.START_ELEMENT;

			String localName = in.getLocalName();
			readFieldXmlElement(in, localName);
		}
	}

	/** Parses the given attribute value and assigns it to the field with the given name. */
	protected void readFieldXmlAttribute(String name, String value) {
		switch (name) {
			case PHONE_ID__XML_ATTR: {
				setPhoneId(value);
				break;
			}
			case SEARCHES__XML_ATTR: {
				setSearches(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> Integer.parseInt(x)).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case AI_SUMMARY__XML_ATTR: {
				setAiSummary(value);
				break;
			}
			case RELATED_NUMBERS__XML_ATTR: {
				setRelatedNumbers(java.util.Arrays.stream(value.split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case PREV__XML_ATTR: {
				setPrev(value);
				break;
			}
			case NEXT__XML_ATTR: {
				setNext(value);
				break;
			}
			case TOP_RATING__XML_ATTR: {
				setTopRating(de.haumacher.phoneblock.db.model.Rating.valueOfProtocol(value));
				break;
			}
			default: {
				// Skip unknown attribute.
			}
		}
	}

	/** Reads the element under the cursor and assigns its contents to the field with the given name. */
	protected void readFieldXmlElement(javax.xml.stream.XMLStreamReader in, String localName) throws javax.xml.stream.XMLStreamException {
		switch (localName) {
			case PHONE_ID__XML_ATTR: {
				setPhoneId(in.getElementText());
				break;
			}
			case NUMBER__XML_ATTR: {
				setNumber(de.haumacher.phoneblock.db.model.PhoneNumer.readPhoneNumer_XmlContent(in));
				break;
			}
			case COMMENTS__XML_ATTR: {
				internalReadCommentsListXml(in);
				break;
			}
			case INFO__XML_ATTR: {
				setInfo(de.haumacher.phoneblock.db.model.PhoneInfo.readPhoneInfo_XmlContent(in));
				break;
			}
			case SEARCHES__XML_ATTR: {
				setSearches(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> Integer.parseInt(x)).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case AI_SUMMARY__XML_ATTR: {
				setAiSummary(in.getElementText());
				break;
			}
			case RELATED_NUMBERS__XML_ATTR: {
				setRelatedNumbers(java.util.Arrays.stream(in.getElementText().split("\\s*,\\s*")).map(x -> x).collect(java.util.stream.Collectors.toList()));
				break;
			}
			case PREV__XML_ATTR: {
				setPrev(in.getElementText());
				break;
			}
			case NEXT__XML_ATTR: {
				setNext(in.getElementText());
				break;
			}
			case TOP_RATING__XML_ATTR: {
				setTopRating(de.haumacher.phoneblock.db.model.Rating.valueOfProtocol(in.getElementText()));
				break;
			}
			default: {
				internalSkipUntilMatchingEndElement(in);
			}
		}
	}

	protected static final void internalSkipUntilMatchingEndElement(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		int level = 0;
		while (true) {
			switch (in.next()) {
				case javax.xml.stream.XMLStreamConstants.START_ELEMENT: level++; break;
				case javax.xml.stream.XMLStreamConstants.END_ELEMENT: if (level == 0) { return; } else { level--; break; }
			}
		}
	}

	private void internalReadCommentsListXml(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		while (true) {
			int event = in.nextTag();
			if (event == javax.xml.stream.XMLStreamConstants.END_ELEMENT) {
				break;
			}

			addComment(de.haumacher.phoneblock.db.model.UserComment.readUserComment_XmlContent(in));
		}
	}

	/** Creates a new {@link SearchResult} and reads properties from the content (attributes and inner tags) of the currently open element in the given {@link javax.xml.stream.XMLStreamReader}. */
	public static SearchResult readSearchResult(javax.xml.stream.XMLStreamReader in) throws javax.xml.stream.XMLStreamException {
		in.nextTag();
		return de.haumacher.phoneblock.db.model.SearchResult.readSearchResult_XmlContent(in);
	}

}
