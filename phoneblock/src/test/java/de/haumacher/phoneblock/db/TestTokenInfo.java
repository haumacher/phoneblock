package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class TestTokenInfo {
	
	@Test
	public void testParse() throws IOException {
		String token = TokenInfo.createToken(1946, new byte[] {0,0,0,0,0,0,0});
		
		assertEquals("pbt_CJoPFjoAAAAAAAAABQ", token);
		
		TokenInfo info = TokenInfo.parse("pbt_CJoPFjoAAAAAAAAABQ");
		assertEquals(1946, info.id);
		assertArrayEquals(new byte[] {0,0,0,0,0,0,0}, info.secret);
	}

}
