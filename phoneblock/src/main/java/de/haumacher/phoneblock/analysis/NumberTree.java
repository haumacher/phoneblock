/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility to remove prefixes from a list of phone numbers and group multiple numbers with the same
 * suffix into a wildcard entry ending with <code>*</code>.
 */
public class NumberTree {
	
	private Node _root = new RootNode();
	
	
	private static class RootNode extends Node {
		@Override
		protected int width() {
			return 11;
		}
		
		@Override
		protected int index(char digit) {
			if (digit == '+') {
				return 10;
			}
			return super.index(digit);
		}
	}
	
	private static class Node {
		private Node[] _next;
		private boolean _wildcard;
		private int _weight;
		private int _heat;

		/**
		 * Appends a next digit to the number represented by this node.
		 * @param weight
		 */
		public Node append(String phone, int index, int weight, int heat) {
			if (index == phone.length()) {
				_weight += weight;
				_heat = Math.max(_heat, heat);
				return this;
			}

			char ch = phone.charAt(index);

			if (_next == null) {
				_next = new Node[width()];
			}

			int digit = index(ch);
			Node next = _next[digit];
			if (next == null) {
				next = new DigitNode(ch);
				_next[digit] = next;
			}

			return next.append(phone, index + 1, weight, heat);
		}

		protected int width() {
			return 10;
		}
		
		/**
		 * Whether this node represents a real number (not a prefix of a number).
		 */
		public boolean isLeaf() {
			return _next == null;
		}

		/**
		 * The given digit as integer (index). 
		 */
		protected int index(char digit) {
			int result = digit - '0';
			if (result < 0 || result > 9) {
				throw new IllegalArgumentException("Not a digit: " + digit);
			}
			return result;
		}

		static class Info {
			private int _depth;
			private int _count;
			private boolean _allowWildcard;

			/** 
			 * Fills info.
			 */
			public void set(int depth, int count, boolean allowWildcard) {
				setDepth(depth);
				setCount(count);
				_allowWildcard = allowWildcard;
			}

			/**
			 * The depth of the node.
			 * 
			 * <p>
			 * A leaf node has depth 0.
			 * </p>
			 */
			int getDepth() {
				return _depth;
			}

			void setDepth(int depth) {
				_depth = depth;
			}

			/**
			 * The number of leaf nodes within the subtree.
			 */
			int getCount() {
				return _count;
			}

			void setCount(int count) {
				_count = count;
			}

			/**
			 * Whether the visited subtree can be covered with a wildcard.
			 */
			public boolean allowWildcard() {
				return _allowWildcard;
			}
			
			public void setAllowWildcard(boolean allowWildcard) {
				_allowWildcard = allowWildcard;
			}
		}
		
		/** 
		 * Marks all subtrees that have depth <= 2 containing more than 3 numbers as wildcard node.
		 * 
		 * @param info Filled with info of this node upon return.
		 */
		public void markWildcards(Info info) {
			if (_next == null) {
				info.set(0, 1, _weight > 0);
				return;
			}
			
			int count = 0;
			int depth = 0;
			int wildcards = 0;
			boolean allowWildcard = true;
			for (Node child : _next) {
				if (child != null) {
					child.markWildcards(info);
					
					depth = Math.max(depth, info.getDepth() + 1);
					count += info.getCount();
					wildcards += child.isWildcard() ? 1 : 0;
					
					if (!info.allowWildcard()) {
						allowWildcard = false;
					}
				}
			}
			
			switch (depth) {
				case 0:
					// Should not happen, because _next is only initialized if a suffix is added.
					_next = null;
					info.set(0, 1, _weight > 0);
					return;
				case 1:
					if (allowWildcard && count >= 3) {
						_wildcard = true;
					}
					break;
				case 2:
					if (allowWildcard && wildcards >= 3) {
						_wildcard = true;
					}
					break;
			}
			
			info.set(depth, count, allowWildcard);
		}

		/**
		 * Whether this is a wildcard node.
		 */
		private boolean isWildcard() {
			return _wildcard;
		}

		/**
		 * Explicitly marks this node as a wildcard node (#377), independent of the
		 * {@link #markWildcards(Info)} threshold.
		 */
		void markWildcard() {
			_wildcard = true;
		}

		/** 
		 * Creates all number patterns represented by the subtree rooted at this node to the given numbers list.
		 */
		public void createBlockEntries(NumberIterator numbers, StringBuilder prefix) {
			int length = prefix.length();
			if (_wildcard) {
				prefix.append('*');
				numbers.accept(prefix.toString(), sumWeight(), maxHeat());
				prefix.setLength(length);
				return;
			}
			if (_next == null) {
				numbers.accept(prefix.toString(), _weight, _heat);
				return;
			}
			for (Node child : _next) {
				if (child != null) {
					child.createBlockEntries(numbers, prefix);
					prefix.setLength(length);
				}
			}
		}

		private int sumWeight() {
			// Includes this node's own weight: a node may be both a complete number (or an
			// explicit wildcard prefix, #377) and the prefix of deeper numbers.
			int sum = _weight;
			if (_next != null) {
				for (Node child : _next) {
					if (child != null) {
						sum += child.sumWeight();
					}
				}
			}
			return sum;
		}

		private int maxHeat() {
			int heat = _heat;
			if (_next != null) {
				for (Node child : _next) {
					if (child != null) {
						heat = Math.max(heat, child.maxHeat());
					}
				}
			}
			return heat;
		}
	}

	interface NumberIterator {
		void accept(String number, int weight, int heat);
	}
	
	private static class DigitNode extends Node {
		private final char _digit;

		/** 
		 * Creates a {@link DigitNode}.
		 */
		public DigitNode(char digit) {
			super();
			_digit = digit;
		}
		
		@Override
		public void createBlockEntries(NumberIterator numbers, StringBuilder prefix) {
			int length = prefix.length();
			prefix.append(_digit);
			super.createBlockEntries(numbers, prefix);
			prefix.setLength(length);
		}
	}
	
	public void insert(String phone) {
		insert(phone, 1, 0);
	}

	public void insert(String phone, int weight, int heat) {
		_root.append(phone, 0, weight, heat);
	}

	/**
	 * Inserts an explicit prefix wildcard (#377): {@code prefix} is emitted as a
	 * '{@code <prefix>*}' block regardless of the {@link #markWildcards()} threshold, so a
	 * user's personal wildcard applies even when automatic wildcard folding is off.
	 */
	public void insertWildcard(String prefix, int weight, int heat) {
		_root.append(prefix, 0, weight, heat).markWildcard();
	}

	public void markWildcards() {
		_root.markWildcards(new Node.Info());
	}

	public List<String> createBlockEntries() {
		ArrayList<String> result = new ArrayList<>();
		NumberIterator sink = (x, weight, heat) -> {
			if (weight > 0) result.add(x);
		};
		createBlockEntries(sink);
		return result;
	}

	/** 
	 * Pushes all numbers to the given sink.
	 */
	public void createBlockEntries(NumberIterator sink) {
		_root.createBlockEntries(sink, new StringBuilder());
	}

	private static final class WeightedNumber {

		private String _number;
		private int _weight;

		/** 
		 * Creates a {@link WeightedNumber}.
		 */
		public WeightedNumber(String number, int weight) {
			_number = number;
			_weight = weight;
		}
		
	}
	
	/**
	 * Maximum prefix length tried during recursive bucket splitting before giving up.
	 *
	 * <p>
	 * Caps pathological cases where many numbers share a very long common prefix. Such a
	 * bucket then stays larger than 9 — accepted, the alternative would be unbounded
	 * recursion.
	 * </p>
	 */
	static final int MAX_BUCKET_PREFIX_DEPTH = 15;

	/**
	 * Initial prefix depth used by {@link #createNumberBlocksByPrefix(int, int)}.
	 */
	static final int INITIAL_BUCKET_PREFIX_DEPTH = 4;

	/**
	 * Build buckets using a deterministic prefix-bucketing algorithm.
	 *
	 * <p>
	 * Each number is placed in the shallowest prefix bucket
	 * (starting at {@value #INITIAL_BUCKET_PREFIX_DEPTH} characters) whose population is
	 * &le; 9. Buckets with more members are split deterministically by extending the prefix
	 * by one character.
	 * </p>
	 *
	 * <p>
	 * Block IDs are the bucket prefix strings — independent of which numbers happen to be
	 * neighbours in the sorted top-K list. This makes IDs stable under typical voting
	 * activity.
	 * </p>
	 */
	public List<NumberBlock> createNumberBlocksByPrefix(int minVotes, int maxEntries) {
		List<WeightedNumber> weighted = new ArrayList<>();
		createBlockEntries((number, votes, heat) -> {
			if (votes < minVotes) {
				return;
			}
			// Top-K ranking for the capped list: by published per-region Heat
			// class (numbers currently active in the *reporting* region claim
			// the slots — where the number originates is irrelevant), votes
			// only as tiebreaker. Personal blacklist entries arrive with a
			// votes weight far above any heat/votes combination and stay on
			// top unconditionally.
			int weight = heat * 10_000 + votes;
			weighted.add(new WeightedNumber(number, weight));
		});

		weighted.sort((n1, n2) -> -Integer.compare(n1._weight, n2._weight));
		List<WeightedNumber> topK = new ArrayList<>(weighted.subList(0, Math.min(maxEntries, weighted.size())));
		List<String> sortedNumbers = new ArrayList<>(topK.size());
		for (WeightedNumber wn : topK) {
			sortedNumbers.add(wn._number);
		}
		sortedNumbers.sort(String::compareTo);

		List<NumberBlock> result = new ArrayList<>();
		bucketize(sortedNumbers, INITIAL_BUCKET_PREFIX_DEPTH, result);
		return result;
	}

	private static void bucketize(List<String> sortedNumbers, int depth, List<NumberBlock> out) {
		// Group by prefix at the current depth, preserving sort order within each group.
		LinkedHashMap<String, List<String>> grouped = new LinkedHashMap<>();
		for (String number : sortedNumbers) {
			String key = number.length() >= depth ? number.substring(0, depth) : number;
			grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(number);
		}
		for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
			String key = entry.getKey();
			List<String> members = entry.getValue();
			if (members.size() <= 9 || key.length() >= MAX_BUCKET_PREFIX_DEPTH) {
				out.add(new NumberBlock(key, members));
			} else {
				bucketize(members, depth + 1, out);
			}
		}
	}

}
