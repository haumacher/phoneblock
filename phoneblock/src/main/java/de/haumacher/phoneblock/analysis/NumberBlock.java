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
	private int _weight;
	private List<String> _numbers = new ArrayList<>();

	/** 
	 * Creates a {@link NumberBlock}.
	 */
	public NumberBlock(String name) {
		_name = name;
	}

	/** 
	 * Adds a new number to this block.
	 * 
	 * @param weight The weight of the given number.
	 */
	public void add(String number, Integer weight) {
		_weight += weight;
		_numbers.add(number);
	}
	
	/**
	 * The total weight of all numbers in this block.
	 */
	public int getWeight() {
		return _weight;
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
}
