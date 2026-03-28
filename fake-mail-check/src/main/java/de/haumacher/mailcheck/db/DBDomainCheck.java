package de.haumacher.mailcheck.db;

import de.haumacher.mailcheck.model.DomainCheck;
import de.haumacher.mailcheck.model.DomainStatus;

public class DBDomainCheck extends DomainCheck {

	public DBDomainCheck(String domainName, String status, long lastChanged, String sourceSystem, String mxHost, String mxIp) {
		setDomainName(domainName);
		setStatus(DomainStatus.valueOf(status.toUpperCase()));
		setLastChanged(lastChanged);
		setSourceSystem(sourceSystem);
		setMxHost(mxHost);
		setMxIP(mxIp);
	}

}
