package de.haumacher.phoneblock.ab;

import de.haumacher.phoneblock.ab.proto.AnswerbotInfo;

public class DBAnswerbotInfo extends AnswerbotInfo {
	
	public DBAnswerbotInfo(
			long id, long userId, 
			boolean enabled, boolean prepareIPv4, int minVotes, boolean wildcards, 
			String registrar, String host, String ip4, String ip6, String realm, 
			boolean registered, String msg, 
			int newCalls, int callsAccepted, long talkTime,
			String userName, String password, 
			String retentionPeriod,
			String dyndnsUser, String dyndnsPassword
	) {
		setId(id);
		setUserId(userId);
		
		setEnabled(enabled);
		setPreferIPv4(prepareIPv4);
		setMinVotes(minVotes);
		setWildcards(wildcards);
		
		setRegistrar(registrar);
		setHost(host);
		setIp4(ip4);
		setIp6(ip6);
		setRealm(realm);
		
		setRegistered(registered);
		setRegisterMsg(msg);
		
		setNewCalls(newCalls);
		setCallsAccepted(callsAccepted);
		setTalkTime(talkTime);

		setUserName(userName);
		setPassword(password);

		setRetentionPeriod(retentionPeriod);

		setDyndnsUser(dyndnsUser);
		setDyndnsPassword(dyndnsPassword);
	}

}
