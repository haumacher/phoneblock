package de.haumacher.phoneblock.db;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.util.Base64;

import de.haumacher.msgbuf.binary.OctetDataReader;
import de.haumacher.msgbuf.binary.OctetDataWriter;

public class TokenInfo {
	public long id;
	public byte[] secret;

	private TokenInfo(long id, byte[] secrets) {
		this.id = id;
		this.secret = secrets;
	}

	public static TokenInfo parse(String token) throws IOException {
		long id;
		byte[] secret;
		{
			byte[] tokenBytes = Base64.getDecoder().decode(token.substring(DB.TOKEN_VERSION.length()));
			ByteArrayInputStream in = new ByteArrayInputStream(tokenBytes);
			OctetDataReader reader = new OctetDataReader(in);
			reader.beginObject();
			reader.nextName();
			id = reader.nextLong();
			reader.nextName();
			secret = reader.nextBinary();
			reader.endObject();
		}
	
		return new TokenInfo(id, secret);
	}

	public static String createToken(long id, byte[] secret) throws IOError {
		try {
			ByteArrayOutputStream tokenStream = new ByteArrayOutputStream();
			OctetDataWriter writer = new OctetDataWriter(tokenStream);
			writer.beginObject();
			writer.name(1);
			writer.value(id);
			writer.name(2);
			writer.value(secret);
			writer.endObject();
			return DB.TOKEN_VERSION + Base64.getEncoder().withoutPadding().encodeToString(tokenStream.toByteArray());
		} catch (IOException ex) {
			throw new IOError(ex);
		}
	}
}