package de.haumacher.phoneblock.mail.check.db;

import de.haumacher.phoneblock.mail.check.model.DomainCheck;

public class DBDomainCheck extends DomainCheck {
	
	public DBDomainCheck(String domainName, boolean disposable, long lastChanged, int sourceSystem, String mxHost, String mxIp) {
		setDomainName(domainName);
		setDisposable(disposable);
		setLastChanged(lastChanged);
		setSourceSystem(sourceSystem);
		setMxHost(mxHost);
		setMxIP(mxIp);
	}

}
