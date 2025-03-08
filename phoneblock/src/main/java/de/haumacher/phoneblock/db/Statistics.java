/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * An entry of the SPAMREPORTS statistics.
 */
public class Statistics {
	
	private final String _state;
	private final int _cnt;
	
	/** 
	 * Creates a {@link Statistics}.
	 */
	public Statistics(String state, int cnt) {
		_state = state;
		_cnt = cnt;
	}

	/**
	 * The amount of spam numbers with given {@link #getConfidence()}.
	 */
	public int getCnt() {
		return _cnt;
	}
	
	/**
	 * The state of the number, either <code>01-reported</code>, or <code>02-blocked</code>.
	 */
	public String getState() {
		return _state;
	}
}
