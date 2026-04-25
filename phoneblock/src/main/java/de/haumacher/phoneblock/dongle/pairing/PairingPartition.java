/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.pairing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Generates the on-flash byte layout of the dongle's "pairing" partition.
 *
 * <p>The corresponding parser lives in the firmware at
 * {@code phoneblock-dongle/firmware/main/pairing_parse.c}. Both sides MUST
 * agree on every constant here — magic, version, length, CRC polynomial,
 * partition size — or a freshly-flashed dongle silently rejects the secret
 * and falls back to mDNS discovery.
 *
 * <p>Layout (4 KB partition; first 28 bytes meaningful, rest is 0xFF
 * because that is the erased-flash state and the firmware ignores it):
 *
 * <pre>
 *   offset  0  uint32  magic   = 0x504B5042  ("PBPK", little-endian on flash)
 *   offset  4  uint16  version = 1
 *   offset  6  uint16  length  = 16
 *   offset  8  uint8[16] secret
 *   offset 24  uint32  crc32   over bytes 0..23
 *                              (CRC-32/ISO-HDLC, identical to {@link CRC32})
 * </pre>
 */
public final class PairingPartition {

	/** Partition size on flash. */
	public static final int PARTITION_SIZE = 4096;

	/** "PBPK" little-endian — also written into the firmware header. */
	public static final int MAGIC = 0x504B5042;

	/** Layout version; bumped when the on-flash format changes. */
	public static final int VERSION = 1;

	/** Fixed secret length. */
	public static final int SECRET_LEN = 16;

	/** Bytes of meaningful header before the 0xFF padding. */
	public static final int HEADER_LEN = 28;

	private PairingPartition() {
		// no instances
	}

	/**
	 * Build the full 4 KB partition image carrying the given 16-byte secret.
	 * The result is exactly {@link #PARTITION_SIZE} bytes long; bytes
	 * {@link #HEADER_LEN}..end are 0xFF (matching the erased-flash state the
	 * firmware sees on OTA-only dongles).
	 *
	 * @param secret a 16-byte cryptographic secret; must not be {@code null}
	 *               and must have length {@link #SECRET_LEN}
	 */
	public static byte[] build(byte[] secret) {
		if (secret == null || secret.length != SECRET_LEN) {
			throw new IllegalArgumentException(
				"secret must be " + SECRET_LEN + " bytes, was "
					+ (secret == null ? "null" : secret.length));
		}

		byte[] out = new byte[PARTITION_SIZE];
		Arrays.fill(out, (byte) 0xFF);

		ByteBuffer header = ByteBuffer.wrap(out, 0, HEADER_LEN)
			.order(ByteOrder.LITTLE_ENDIAN);
		header.putInt(MAGIC);
		header.putShort((short) VERSION);
		header.putShort((short) SECRET_LEN);
		header.put(secret);

		CRC32 crc = new CRC32();
		crc.update(out, 0, 24);
		header.putInt((int) crc.getValue());

		return out;
	}
}
