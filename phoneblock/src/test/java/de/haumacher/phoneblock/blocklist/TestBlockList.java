/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.blocklist;

import java.util.Iterator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link BlockList}.
 */
@SuppressWarnings("javadoc")
public class TestBlockList {

	@Test
	public void testCreate() {
		BlockList list = new BlockList(100);
		
		list.accept("");
		list.accept("0");
		
		list.accept("0300001");
		list.accept("0400001");
		list.accept("0500001");
		list.accept("0600001");
		list.accept("0700001");
		
		list.accept("0700042");
		list.accept("0800042*");
		list.accept("0900042**");
		
		Iterator<Bucket> buckets = list.iterator();
		
		Bucket bucket0 = buckets.next();
		Assertions.assertEquals(0, bucket0.getIndex());
		Assertions.assertEquals(2, bucket0.size());

		Bucket bucket1 = buckets.next();
		Assertions.assertEquals(1, bucket1.getIndex());
		Assertions.assertEquals(5, bucket1.size());
		
		Bucket bucket42 = buckets.next();
		Assertions.assertEquals(42, bucket42.getIndex());
		Assertions.assertEquals(3, bucket42.size());
		
		Assertions.assertFalse(buckets.hasNext());
	}
	
	@Test
	public void testOverlay() {
		BlockList list = new BlockList(10);
		
		list.accept("030000");
		list.accept("030001");
		
		BlockList overlay = list.createOverlay();
		
		overlay.accept("040000");
		overlay.remove("030001");
		
		Iterator<Bucket> buckets = overlay.iterator();
		
		Bucket bucket0 = buckets.next();
		Assertions.assertEquals(0, bucket0.getIndex());
		Assertions.assertEquals(2, bucket0.size());

		Assertions.assertFalse(buckets.hasNext());
	}
	
}
