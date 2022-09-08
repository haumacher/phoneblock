import fetch from 'node-fetch';
import tr from 'tr-064-async';
import xml2js from 'xml2js';
import btoa from 'btoa';

// Options for connecting to the Fritz!Box.
var fbOptions = {
	host: 'fritz.box',
	port: 49000,
	ssl: false,
	user: 'user',
	password: 'password'
};

// Options for connecting to PhoneBlock.
var pbOptions = {
	user: "user",
	password: "password"
};

async function updateCallList() {
	const pbAuth = 'Basic ' + btoa(pbOptions.user + ":" + pbOptions.password);
	
	const stateResponse = await fetch("https://phoneblock.haumacher.de/phoneblock/callreport", {
	    method: 'GET',
	    cache: 'no-cache',
	    credentials: 'include',
	    headers: {
	      'Authorization': pbAuth
	    }
	});
	
	if (!stateResponse.ok) {
	  throw new Error('Cannot retrieve call-list state: ' + stateResponse.status + " " + stateResponse.statusText);
	}
	    
	const state = await stateResponse.json();
	const timestamp = state.timestamp;
	const lastid = state.lastid;
	
	console.log("Received report state: timestamp=" + timestamp + ", lastid=" + lastid);
	
	const fritzbox = new tr.Fritzbox(fbOptions);
	
	console.log('Initializing Fritz!Box device');
	await fritzbox.initTR064Device();
		
	console.log('Retrieving call-list URL from Fritz!Box.');
	const ontel = fritzbox.services["urn:dslforum-org:service:X_AVM-DE_OnTel:1"];
	const result = await ontel.actions.GetCallList();
	var url = result.NewCallListURL;
	if (timestamp != null && lastid != null) {
		url = url + "&timestamp=" + timestamp + "&id=" + lastid;
	}
	
	console.log("Feching call-list URL from Fritz!Box: " + url);
	const response = await fetch(url);
	const body = await response.text();
	
	console.log("Retrieved call-list from Fritz!Box.");
	const calllist = await xml2js.parseStringPromise(body);
	if (calllist.root.Call == undefined) {
		console.log("No more blocked calls, exiting.");
		return;
	}
	const blocked = calllist.root.Call.filter((call) => call.Type[0] == 10);
	
	var callers = new Set();
	blocked.forEach((call) => callers.add(call.Caller[0]));
	
	var info = {
		timestamp: calllist.root.timestamp[0],
		lastid: calllist.root.Call[0].Id[0],
		callers: [...callers]
	}
	
	console.log(info);
	
	console.log("Uploading call list.");
	const updateResponse = await fetch("https://phoneblock.haumacher.de/phoneblock/callreport", {
	    method: 'PUT',
	    cache: 'no-cache',
	    credentials: 'include',
	    headers: {
	      'Content-Type': 'application/json',
	      'Authorization': pbAuth
	    },
	    body: JSON.stringify(info) 
	});
	
	if (!updateResponse.ok) {
	  throw new Error('Failed to upload call-list: ' + updateResponse.status + " " + updateResponse.statusText);
	}
	
	console.log("Done.");
}

await updateCallList();
