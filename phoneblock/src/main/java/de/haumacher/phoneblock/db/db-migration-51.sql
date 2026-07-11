-- English versions of the diagnostics help-mail templates. The MailNotifier picks
-- DIAG_TEMPLATE by the user's language and falls back to 'de'; adding 'en' closes
-- the gap for non-German users. Further languages can be added the same way.
INSERT INTO DIAG_TEMPLATE (TEMPLATE_KEY, LANG, SUBJECT, BODY, UPDATED) VALUES
	('help-register-rejected', 'en',
	 'Your PhoneBlock dongle cannot register',
	 '<p>Hello,</p><p>your PhoneBlock dongle (device {deviceId}) has been trying to register with your Fritz!Box for several days without success - the registration (SIP REGISTER) is being rejected.</p><p>Please check the user name and the extension of the IP phone you set up for PhoneBlock, in your Fritz!Box under "Telephony / Telephony devices".</p><p>Best regards,<br/>Your PhoneBlock team</p>',
	 0),
	('help-internet-exposed', 'en',
	 'Security notice: your PhoneBlock dongle is reachable from the internet',
	 '<p>Hello,</p><p>we have detected that the web interface of your PhoneBlock dongle (device {deviceId}) is reachable from the internet: the device is receiving automated requests from vulnerability scanners.</p><p><strong>This is a security risk.</strong> The dongle is meant to run on your home network and does not need to be reachable from the internet.</p><p>Please check your router settings (e.g. Fritz!Box) and remove any port forwarding or "Exposed Host"/DMZ rule that points to the dongle.</p><p>Best regards,<br/>Your PhoneBlock team</p>',
	 0),
	('help-device-silent', 'en',
	 'Is your PhoneBlock dongle still there?',
	 '<p>Hello,</p><p>your PhoneBlock dongle (device {deviceId}) has not contacted PhoneBlock for several days.</p><p>If you have intentionally taken the device out of service, you can ignore this message.</p><p>Otherwise, please check that the dongle has power and is connected to your home network (Wi-Fi/LAN).</p><p>Best regards,<br/>Your PhoneBlock team</p>',
	 0);
