package de.haumacher.phoneblock.ab;

import de.haumacher.phoneblock.ab.proto.AnswerbotInfo;

public class DBAnswerbotInfo extends AnswerbotInfo {
	
	public DBAnswerbotInfo(
			long id, 
			boolean enabled, String registrar, String host, String ip4, String ip6, String realm, 
			boolean registered, String msg, 
			int callsAccepted, 
			String userName, String password, String dyndnsUser, String dyndnsPassword
	) {
		setId(id);
		setEnabled(enabled);
		setRegistrar(registrar);
		setHost(host);
		setIp4(ip4);
		setIp6(ip6);
		setRealm(realm);
		setRegistered(registered);
		setRegisterMsg(msg);
		setCallsAccepted(callsAccepted);

		setUserName(userName);
		setPassword(password);

		setDyndnsUser(dyndnsUser);
		setDyndnsPassword(dyndnsPassword);
	}

}
