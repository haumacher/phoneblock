package de.haumacher.phoneblock.db;

/**
 * Persistent record of a beta-tester application for the PhoneBlock dongle.
 */
public class DBDongleApplication {

	private long id;
	private long userId;
	private long created;

	private String name;
	private String street;
	private String zip;
	private String city;
	private String country;

	private String provider;
	private String providerOther;
	private String connectionType;
	private String routerKind;
	private String routerModel;
	private String spamFrequency;

	private String skillLevel;
	private boolean allowPublish;

	private String notes;

	private String status;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getUserId() {
		return userId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
	}

	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getProviderOther() {
		return providerOther;
	}

	public void setProviderOther(String providerOther) {
		this.providerOther = providerOther;
	}

	public String getConnectionType() {
		return connectionType;
	}

	public void setConnectionType(String connectionType) {
		this.connectionType = connectionType;
	}

	public String getRouterKind() {
		return routerKind;
	}

	public void setRouterKind(String routerKind) {
		this.routerKind = routerKind;
	}

	public String getRouterModel() {
		return routerModel;
	}

	public void setRouterModel(String routerModel) {
		this.routerModel = routerModel;
	}

	public String getSpamFrequency() {
		return spamFrequency;
	}

	public void setSpamFrequency(String spamFrequency) {
		this.spamFrequency = spamFrequency;
	}

	public String getSkillLevel() {
		return skillLevel;
	}

	public void setSkillLevel(String skillLevel) {
		this.skillLevel = skillLevel;
	}

	public boolean isAllowPublish() {
		return allowPublish;
	}

	public void setAllowPublish(boolean allowPublish) {
		this.allowPublish = allowPublish;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
