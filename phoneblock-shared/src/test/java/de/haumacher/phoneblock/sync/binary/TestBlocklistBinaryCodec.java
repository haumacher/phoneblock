/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.sync.binary.BlocklistBinaryDecoder.DecodedBlocklist;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryDecoder.DecodedList;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;

/**
 * Tests the on-disk encoding produced by {@link BlocklistBinaryEncoder} and the
 * symmetric parser in {@link BlocklistBinaryDecoder}.
 */
class TestBlocklistBinaryCodec {

	@Test
	void headerStartsWithAsciiMagicAndVersion() throws IOException {
		byte[] bytes = encode(List.of(new Entry("4930123456", false, true)));
		assertEquals('P', bytes[0]);
		assertEquals('B', bytes[1]);
		assertEquals('B', bytes[2]);
		assertEquals('L', bytes[3]);
		assertEquals(BlocklistBinaryFormat.VERSION, bytes[4] & 0xFF);
		assertEquals(0, bytes[5] & 0xFF);
	}

	@Test
	void emptyInputProducesHeaderOnly() throws IOException {
		byte[] bytes = encode(List.of());
		assertEquals(BlocklistBinaryFormat.HEADER_SIZE, bytes.length);

		DecodedBlocklist decoded = decode(bytes);
		assertEquals(0, decoded.community().header().exactCount());
		assertEquals(0, decoded.community().header().prefixCount());
		assertEquals(0, decoded.community().header().prefixLengths());
		assertEquals(0, decoded.personal().header().exactCount());
		assertEquals(0, decoded.personal().header().prefixCount());
		assertEquals(0, decoded.personal().header().prefixLengths());
	}

	@Test
	void communityRoundTripSet() throws IOException {
		List<Entry> input = List.of(
			new Entry("4930123456", false, true),
			new Entry("4915112345678", false, true),
			new Entry("00188867490", false, true),
			new Entry("12345", true, true),
			new Entry("4930999", true, true));

		byte[] bytes = encode(input);
		DecodedBlocklist decoded = decode(bytes);

		assertEquals(3, decoded.community().header().exactCount());
		assertEquals(2, decoded.community().header().prefixCount());
		assertEquals(0, decoded.personal().header().exactCount());
		assertEquals(0, decoded.personal().header().prefixCount());

		Set<String> expected = toIdentitySet(input);
		Set<String> actual = toIdentitySet(BlocklistBinaryDecoder.toEntries(decoded.community()));
		assertEquals(expected, actual);

		assertSortedUnsignedAscending(decoded.community().exactRecords());
		assertSortedUnsignedAscending(decoded.community().prefixRecords());
	}

	@Test
	void personalListRoundTrip() throws IOException {
		List<Entry> personal = List.of(
			new Entry("4930", true, false),
			new Entry("4930999", true, true),
			new Entry("4930123", false, true),
			new Entry("18886749072", false, false));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BlocklistBinaryEncoder.write(out, Collections.emptyList(), personal);
		DecodedBlocklist decoded = decode(out.toByteArray());

		assertEquals(0, decoded.community().header().exactCount());
		assertEquals(2, decoded.personal().header().exactCount());
		assertEquals(2, decoded.personal().header().prefixCount());

		Set<String> expected = toIdentitySet(personal);
		Set<String> actual = toIdentitySet(BlocklistBinaryDecoder.toEntries(decoded.personal()));
		assertEquals(expected, actual);
	}

	@Test
	void communityAndPersonalKeepSeparate() throws IOException {
		List<Entry> community = List.of(
			new Entry("4930123", false, true),
			new Entry("4930", true, true));
		List<Entry> personal = List.of(
			new Entry("4930", true, false),
			new Entry("100", false, false));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BlocklistBinaryEncoder.write(out, community, personal);
		DecodedBlocklist decoded = decode(out.toByteArray());

		// Same 4930*-key sits in both lists with opposing colors.
		assertEquals(1, decoded.community().exactRecords().length);
		assertEquals(1, decoded.community().prefixRecords().length);
		assertEquals(1, decoded.personal().exactRecords().length);
		assertEquals(1, decoded.personal().prefixRecords().length);

		assertTrue(BlocklistRecord.isBlack(decoded.community().prefixRecords()[0]),
			"community 4930* is black");
		assertTrue(!BlocklistRecord.isBlack(decoded.personal().prefixRecords()[0]),
			"personal 4930* is white");
	}

	@Test
	void deduplicatesIdenticalEntries() throws IOException {
		List<Entry> input = List.of(
			new Entry("4930123", false, true),
			new Entry("4930123", false, true),
			new Entry("4930", true, true),
			new Entry("4930", true, true));

		DecodedBlocklist decoded = decode(encode(input));
		assertEquals(1, decoded.community().exactRecords().length);
		assertEquals(1, decoded.community().prefixRecords().length);
	}

	@Test
	void prefixLengthsBitmapReflectsActualLengths() throws IOException {
		List<Entry> input = List.of(
			new Entry("49", true, true),
			new Entry("4930", true, true),
			new Entry("12345", true, true),
			new Entry("987654", false, true));

		DecodedBlocklist decoded = decode(encode(input));
		int bitmap = decoded.community().header().prefixLengths();
		assertTrue((bitmap & (1 << 2)) != 0, "length 2 present");
		assertTrue((bitmap & (1 << 4)) != 0, "length 4 present");
		assertTrue((bitmap & (1 << 5)) != 0, "length 5 present");
		assertEquals(0, bitmap & ~((1 << 2) | (1 << 4) | (1 << 5)),
			"no other bits set");
		assertEquals(0, decoded.personal().header().prefixLengths(),
			"personal list is empty -> no prefix lengths");
	}

	@Test
	void rejectsBadMagic() {
		byte[] bytes = new byte[BlocklistBinaryFormat.HEADER_SIZE];
		Arrays.fill(bytes, (byte) 0xFF);
		assertThrows(IllegalArgumentException.class, () -> decode(bytes));
	}

	@Test
	void rejectsUnknownVersion() throws IOException {
		byte[] bytes = encode(List.of());
		bytes[4] = 99;
		assertThrows(IllegalArgumentException.class, () -> decode(bytes));
	}

	@Test
	void recordsAreLittleEndian() throws IOException {
		Entry entry = new Entry("12345", false, true);
		long expectedRecord = BlocklistRecord.record(BlocklistRecord.key("12345"), true);

		byte[] bytes = encode(List.of(entry));
		byte[] recordBytes = Arrays.copyOfRange(
			bytes, BlocklistBinaryFormat.HEADER_SIZE,
			BlocklistBinaryFormat.HEADER_SIZE + BlocklistBinaryFormat.RECORD_SIZE);

		byte[] expectedBytes = new byte[8];
		for (int i = 0; i < 8; i++) {
			expectedBytes[i] = (byte) (expectedRecord >>> (8 * i));
		}
		assertArrayEquals(expectedBytes, recordBytes);
	}

	@Test
	void truncatedFileFailsToDecode() throws IOException {
		byte[] bytes = encode(List.of(new Entry("4930123", false, true)));
		byte[] truncated = Arrays.copyOfRange(bytes, 0, bytes.length - 3);
		assertThrows(IOException.class, () -> decode(truncated));
	}

	private static byte[] encode(List<Entry> communityEntries) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BlocklistBinaryEncoder.write(out, communityEntries);
		return out.toByteArray();
	}

	private static DecodedBlocklist decode(byte[] bytes) throws IOException {
		return BlocklistBinaryDecoder.read(new ByteArrayInputStream(bytes));
	}

	private static Set<String> toIdentitySet(List<Entry> entries) {
		Set<String> result = new HashSet<>();
		for (Entry e : entries) {
			result.add(e.digits() + "|" + (e.wildcard() ? 'W' : 'E') + "|" + (e.black() ? 'B' : 'L'));
		}
		return result;
	}

	private static void assertSortedUnsignedAscending(long[] records) {
		for (int i = 1; i < records.length; i++) {
			assertTrue(Long.compareUnsigned(records[i - 1], records[i]) < 0,
				"records not strictly unsigned-ascending at index " + i);
		}
	}

}
