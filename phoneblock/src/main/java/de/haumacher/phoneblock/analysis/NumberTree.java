/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility to remove prefixes from a list of phone numbers and group multiple numbers with the same
 * suffix into a wildcard entry ending with <code>*</code>.
 */
public class NumberTree {
	
	private Node _root = new Node();
	
	
	private static class Node {
		private Node[] _next;
		private boolean _wildcard;

		/** 
		 * Appends a next digit to the number represented by this node.
		 */
		public Node append(String phone, int index) {
			if (index == phone.length()) {
				return this;
			}
			
			char ch = phone.charAt(index);
			
			if (_next == null) {
				_next = new Node[10];
			}
			
			int digit = index(ch);
			Node next = _next[digit];
			if (next == null) {
				next = new DigitNode(ch);
				_next[digit] = next;
			}
			
			return next.append(phone, index + 1);
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
		private static int index(char digit) {
			int result = digit - '0';
			if (result < 0 || result > 9) {
				throw new IllegalArgumentException("Not a digit: " + digit);
			}
			return result;
		}

		static class Info {
			private int _depth;
			private int _count;

			/** 
			 * Fills info.
			 */
			public void set(int depth, int count) {
				setDepth(depth);
				setCount(count);
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
		}
		
		/** 
		 * Marks all subtrees that have depth <= 2 containing more than 3 numbers as wildcard node.
		 * 
		 * @param info Filled with info of this node upon return.
		 */
		public void markWildcards(Info info) {
			if (_next == null) {
				info.set(0, 1);
				return;
			}
			
			int count = 0;
			int depth = 0;
			int wildcards = 0;
			for (Node child : _next) {
				if (child != null) {
					child.markWildcards(info);
					
					depth = Math.max(depth, info.getDepth() + 1);
					count += info.getCount();
					wildcards += child.isWidlcard() ? 1 : 0;
				}
			}
			
			switch (depth) {
				case 0:
					// Should not happen, because _next is only initialized if a suffix is added.
					_next = null;
					info.set(0, 1);
					return;
				case 1:
					if (count >= 3) {
						_wildcard = true;
					}
					break;
				case 2:
					if (wildcards >= 3) {
						_wildcard = true;
					}
					break;
			}
			
			info.set(depth, count);
		}

		/** 
		 * Whether this is a wildcard node.
		 */
		private boolean isWidlcard() {
			return _wildcard;
		}

		/** 
		 * Creates all number patterns represented by the subtree rooted at this node to the given numbers list.
		 */
		public void createBlockEntries(Consumer<String> numbers, StringBuilder prefix) {
			int length = prefix.length();
			if (_wildcard) {
				prefix.append('*');
				numbers.accept(prefix.toString());
				prefix.setLength(length);
				return;
			}
			if (_next == null) {
				numbers.accept(prefix.toString());
				return;
			}
			for (Node child : _next) {
				if (child != null) {
					child.createBlockEntries(numbers, prefix);
					prefix.setLength(length);
				}
			}
		}
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
		public void createBlockEntries(Consumer<String> numbers, StringBuilder prefix) {
			int length = prefix.length();
			prefix.append(_digit);
			super.createBlockEntries(numbers, prefix);
			prefix.setLength(length);
		}
	}
	
	public void insert(String phone) {
		_root.append(phone, 0);
	}
	
	public void markWildcards() {
		_root.markWildcards(new Node.Info());
	}
	
	public List<String> createBlockEntries() {
		ArrayList<String> result = new ArrayList<>();
		Consumer<String> sink = x -> result.add(x);
		createBlockEntries(sink);
		return result;
	}

	/** 
	 * Pushes all numbers to the given sink.
	 */
	public void createBlockEntries(Consumer<String> sink) {
		_root.createBlockEntries(sink, new StringBuilder());
	}

}
