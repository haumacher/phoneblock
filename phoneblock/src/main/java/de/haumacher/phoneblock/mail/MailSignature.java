package de.haumacher.phoneblock.mail;

import java.security.interfaces.RSAPrivateKey;

import org.simplejavamail.utils.mail.dkim.DkimSigner;

public class MailSignature {
	private final String _signingSelector;
	private final String _signingDomain;
	private final RSAPrivateKey _signingKey;

	public MailSignature(String signingSelector, String signingDomain, RSAPrivateKey signingKey) {
		_signingSelector = signingSelector;
		_signingDomain = signingDomain;
		_signingKey = signingKey;
	}

	public DkimSigner createSigner() {
		return new DkimSigner(_signingDomain, _signingSelector, _signingKey);
	}
	
	@Override
	public String toString() {
		return _signingSelector + "._domainkey." + _signingDomain;
	}
}