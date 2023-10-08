/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * A block of phone numbers that can be assigned to a single address book entry.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class NumberBlock {
	
	private final String _name;
	private List<String> _numbers = new ArrayList<>();
	
	StringBuilder _prefixBuffer;
	private String _title;

	/** 
	 * Creates a {@link NumberBlock}.
	 */
	public NumberBlock(String name) {
		_name = name;
	}

	/** 
	 * Adds a new number to this block.
	 */
	public void add(String number) {
		_numbers.add(number);
		if (_prefixBuffer == null) {
			_prefixBuffer = new StringBuilder();
			_prefixBuffer.append(number);
		} else {
			for (int n = 0, cnt = Math.min(_prefixBuffer.length(), number.length()); n < cnt; n++) {
				if (_prefixBuffer.charAt(n) != number.charAt(n)) {
					_prefixBuffer.setLength(n);
					break;
				}
			}
		}
	}
	
	/**
	 * Description of the range of numbers in this block.
	 */
	public String getBlockTitle() {
		if (_title == null) {
			_title = computeTitle();
		}
		return _title;
	}

	private String computeTitle() {
		if (_prefixBuffer == null) {
			return "";
		} else {
			String prefix = _prefixBuffer.toString();

			int size = _numbers.size();
			if (size > 1) {
				String first = _numbers.get(0);
				String last = _numbers.get(size - 1);
				int prefixLength = prefix.length();
				return prefix + "(" + first.substring(prefixLength) + ".." + last.substring(prefixLength) + ")";
			}
			
			return prefix;
		}
	}

	/** 
	 * The amount of numbers in this block.
	 */
	public int size() {
		return _numbers.size();
	}

	/** 
	 * A common prefix of all numbers in this block.
	 */
	public String getName() {
		return _name;
	}

	/** 
	 * All numbers in this block.
	 */
	public List<String> getNumbers() {
		return _numbers;
	}

	/** 
	 * An identifier for this block.
	 */
	public String getBlockId() {
		return _numbers.get(0);
	}
}
