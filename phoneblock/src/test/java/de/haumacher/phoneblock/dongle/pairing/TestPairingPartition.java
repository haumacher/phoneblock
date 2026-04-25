/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.pairing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * Pins down the on-flash byte layout the firmware-side parser expects (see
 * {@code phoneblock-dongle/firmware/main/pairing_parse.{c,h}}). Any drift
 * between the two will silently break first-time install pairing, so the
 * tests check magic / version / length / CRC byte-for-byte rather than
 * round-tripping through Java alone.
 */
class TestPairingPartition {

	@Test
	void rejectsWrongSecretLength() {
		assertThrows(IllegalArgumentException.class, () -> PairingPartition.build(null));
		assertThrows(IllegalArgumentException.class, () -> PairingPartition.build(new byte[15]));
		assertThrows(IllegalArgumentException.class, () -> PairingPartition.build(new byte[17]));
	}

	@Test
	void imageHasPartitionSize() {
		byte[] img = PairingPartition.build(new byte[16]);
		assertEquals(PairingPartition.PARTITION_SIZE, img.length);
	}

	@Test
	void tailIs0xFFPadding() {
		byte[] img = PairingPartition.build(new byte[16]);
		for (int i = PairingPartition.HEADER_LEN; i < img.length; i++) {
			assertEquals((byte) 0xFF, img[i],
				"byte " + i + " should be 0xFF padding");
		}
	}

	@Test
	void headerHasFirmwareCompatibleBytes() {
		byte[] secret = {
			0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
			(byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB,
			(byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
		};
		byte[] img = PairingPartition.build(secret);

		ByteBuffer buf = ByteBuffer.wrap(img).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals(PairingPartition.MAGIC,      buf.getInt(0));
		assertEquals(PairingPartition.VERSION,    buf.getShort(4) & 0xFFFF);
		assertEquals(PairingPartition.SECRET_LEN, buf.getShort(6) & 0xFFFF);
		assertArrayEquals(secret, Arrays.copyOfRange(img, 8, 24));

		// CRC must match what the firmware computes (CRC-32/ISO-HDLC over
		// the first 24 bytes — same polynomial as zlib, esptool, and the
		// firmware's pairing_crc32() function).
		CRC32 crc = new CRC32();
		crc.update(img, 0, 24);
		int expectedCrc = (int) crc.getValue();
		assertEquals(expectedCrc, buf.getInt(24));
	}

	@Test
	void crc32IsoHdlcKnownVectors() {
		// Same vectors the firmware test pins down. If these ever
		// disagree, server and dongle have diverged and pairing breaks.
		assertEquals(0x00000000, crc32(""));
		assertEquals(0xE8B7BE43, crc32("a"));
		assertEquals(0xCBF43926, crc32("123456789"));
	}

	private static int crc32(String s) {
		CRC32 c = new CRC32();
		c.update(s.getBytes());
		return (int) c.getValue();
	}
}
