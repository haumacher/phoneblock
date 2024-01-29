package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.ab.proto.CallInfo;

public class DBCallInfo extends CallInfo {
	
	public DBCallInfo(String caller, long started, long duration) {
		setCaller(caller);
		setStarted(started);
		setDuration(duration);
	}

}
