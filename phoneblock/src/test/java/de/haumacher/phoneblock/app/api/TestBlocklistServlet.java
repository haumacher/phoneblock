/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link BlocklistServlet}'s threshold clamping: an arbitrary
 * requested {@code minDirect}/{@code minRange} is snapped up to the nearest
 * allowed option so the community-binary cache key stays shared.
 */
class TestBlocklistServlet {

	@Test
	void clampDirectCeilsToNearestOption() {
		int[] o = BlocklistServlet.MIN_DIRECT_OPTIONS; // {2,4,10,20,50,100}
		assertEquals(2, BlocklistServlet.clampToAllowed(1, o), "below min → min");
		assertEquals(2, BlocklistServlet.clampToAllowed(2, o));
		assertEquals(4, BlocklistServlet.clampToAllowed(3, o), "3 ceils to 4");
		assertEquals(4, BlocklistServlet.clampToAllowed(4, o));
		assertEquals(10, BlocklistServlet.clampToAllowed(7, o), "7 ceils to 10");
		assertEquals(10, BlocklistServlet.clampToAllowed(10, o));
		assertEquals(100, BlocklistServlet.clampToAllowed(99, o));
		assertEquals(100, BlocklistServlet.clampToAllowed(100, o));
		assertEquals(100, BlocklistServlet.clampToAllowed(200, o), "above max → capped at max");
	}

	@Test
	void clampRangeCeilsAndKeepsOff() {
		int[] o = BlocklistServlet.MIN_RANGE_OPTIONS; // {0,10,20,50,100,500}
		assertEquals(0, BlocklistServlet.clampToAllowed(0, o), "0 stays off");
		assertEquals(10, BlocklistServlet.clampToAllowed(1, o), "1 ceils to 10");
		assertEquals(10, BlocklistServlet.clampToAllowed(5, o));
		assertEquals(10, BlocklistServlet.clampToAllowed(10, o));
		assertEquals(100, BlocklistServlet.clampToAllowed(60, o), "60 ceils to 100");
		assertEquals(500, BlocklistServlet.clampToAllowed(500, o));
		assertEquals(500, BlocklistServlet.clampToAllowed(600, o), "above max → capped at max");
	}

	@Test
	void clampBytesFloorsToNearestBudget() {
		int[] o = BlocklistServlet.MAX_BYTES_OPTIONS; // {64k,128k,256k,384k,512k}
		assertEquals(65536, BlocklistServlet.clampDownToAllowed(1000, o), "below min → min (pre-check backstops)");
		assertEquals(65536, BlocklistServlet.clampDownToAllowed(65536, o));
		assertEquals(65536, BlocklistServlet.clampDownToAllowed(131071, o), "just under 128k floors to 64k");
		assertEquals(131072, BlocklistServlet.clampDownToAllowed(131072, o));
		assertEquals(262144, BlocklistServlet.clampDownToAllowed(300000, o), "300k floors to 256k");
		assertEquals(524288, BlocklistServlet.clampDownToAllowed(524288, o));
		assertEquals(524288, BlocklistServlet.clampDownToAllowed(900000, o), "above max → capped at max");
	}

}
