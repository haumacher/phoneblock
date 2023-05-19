/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.blocklist;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Hashtable-like structure of phone numbers assigned to buckets.
 * 
 * <p>
 * A bucket is a list of numbers with the same hash value. A bucket is converted to a contact in the block list.
 * </p>
 */
public class BlockList implements Consumer<String>, Iterable<Bucket> {
	
	private final int _bucketCnt;
	
	private BucketImpl[] _buckets;

	private int _indexDigits;

	/** 
	 * Creates a {@link BlockList}.
	 *
	 * @param buckets The number of address book entries.
	 */
	public BlockList(int buckets) {
		int cnt = 10;
		_indexDigits = 1;
		
		while (cnt < buckets) {
			_indexDigits++;
			cnt *= 10;
		}
		
		_bucketCnt = buckets;
		_buckets = new BucketImpl[buckets];
	}
	
	@Override
	public void accept(String number) {
		int index = index(number);
		BucketImpl bucket = localBucket(index);
		if (bucket == null) {
			bucket = createBucket(index);
		}
		bucket.add(number);
	}

	/**
	 * Removes a number for the block list.
	 */
	public void remove(String number) {
		int index = index(number);
		BucketImpl bucket = localBucket(index);
		if (bucket == null) {
			bucket = createBucket(index);
		}
		bucket.remove(number);
	}
	
	@Override
	public Iterator<Bucket> iterator() {
		return new Iterator<Bucket>() {
			
			int _index = 0;

			@Override
			public boolean hasNext() {
				findNext();
				return inRange();
			}

			private boolean inRange() {
				return _index < _bucketCnt;
			}

			private void findNext() {
				while (inRange()) {
					BucketImpl bucket = getBucket(_index);
					if (bucket != null && bucket.size() > 0) {
						break;
					}
					_index++;
				}
			}

			@Override
			public Bucket next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				return getBucket(_index++);
			}
		};
	}

	BucketImpl getBucket(int index) {
		return localBucket(index);
	}
	
	final BucketImpl localBucket(int index) {
		return _buckets[index];
	}

	BucketImpl createBucket(int index) {
		BucketImpl bucket = new BucketImpl(index);
		_buckets[index] = bucket;
		return bucket;
	}

	/** 
	 * The hash function computing the bucket index of a given number. 
	 */
	private int index(String number) {
		int last = number.length() - 1;
		while (last >= 0 && !Character.isDigit(number.charAt(last))) {
			last--;
		}
		
		int index = 0;
		int pos = Math.max(0, last - _indexDigits + 1);
		while (pos <= last) {
			int digit = number.charAt(pos++) - '0';
			
			index *= 10;
			index += digit;
		}
		
		return index % _bucketCnt;
	}

	/** 
	 * Computes a hash code of the given number to find the bucket to insert that number to.
	 */
	protected int hash(String number) {
		return number.hashCode();
	}
	
	public BlockList createOverlay() {
		return new BlockListOverlay(this);
	}
	
	private static class BlockListOverlay extends BlockList {

		private BlockList _orig;

		/** 
		 * Creates a {@link BlockListOverlay}.
		 */
		public BlockListOverlay(BlockList orig) {
			super(orig._bucketCnt);
			_orig = orig;
		}
		
		@Override
		BucketImpl getBucket(int index) {
			BucketImpl local = super.getBucket(index);
			if (local != null) {
				return local;
			}
			
			return _orig.getBucket(index);
		}

		@Override
		BucketImpl createBucket(int index) {
			BucketImpl result = super.createBucket(index);
			BucketImpl origBucket = _orig.localBucket(index);
			if (origBucket != null) {
				result.addAll(origBucket);
			}
			return result;
		}
	}
	
	private static class BucketImpl extends ArrayList<String> implements Bucket {
		private int _index;

		/** 
		 * Creates a {@link BlockList.BucketImpl}.
		 */
		public BucketImpl(int index) {
			_index = index;
		}
		
		@Override
		public int getIndex() {
			return _index;
		}
		
	}

}
