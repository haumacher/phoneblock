/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * An entry of the SPAMREPORTS statistics.
 */
public class Statistics {
	
	private final int _cnt;
	private final int _contidence;
	
	/** 
	 * Creates a {@link Statistics}.
	 */
	public Statistics(int cnt, int contidence) {
		_cnt = cnt;
		_contidence = contidence;
	}

	/**
	 * The amount of spam numbers with given {@link #getContidence()}.
	 */
	public int getCnt() {
		return _cnt;
	}
	
	/**
	 * The confidence level of the spam report: 0 - reported, 1 - confirmed, 2 - certain
	 */
	public int getContidence() {
		return _contidence;
	}

}
