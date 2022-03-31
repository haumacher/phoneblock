/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public enum Depth {
	EMPTY, IMMEDIATES, INFINITY;

	/** 
	 * TODO
	 *
	 * @param depthValue
	 * @return
	 */
	public static Depth fromHeader(String depthValue) {
		if (depthValue == null) {
			return INFINITY;
		}
		switch (depthValue) {
		case "0": return EMPTY;
		case "1": return IMMEDIATES;
		case "infinity": return INFINITY;
		}
		throw new IllegalArgumentException("Invalid depth: " + depthValue);
	}
}
