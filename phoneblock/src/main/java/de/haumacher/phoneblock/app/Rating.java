/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public enum Rating {

	A_LEGITIMATE("Seriös", "is-success", -2), 
	B_MISSED("Unbekannt", "is-light", 0), 
	C_PING("Ping-Anruf", "is-dark", 2), 
	D_POLL("Umfrage", "is-info is-light", 2), 
	E_ADVERTISING("Werbung", "is-info", 2), 
	F_GAMBLE("Gewinnspiel", "is-warning", 2), 
	G_FRAUD("Betrug", "is-danger", 4);

	private final int _votes;
	private final String _label;
	private final String _cssClass;

	/** 
	 * Creates a {@link Rating}.
	 * @param label The label for this {@link Rating} to display. 
	 * @param cssClass The CSS class to set when showing this {@link Rating}.
	 */
	Rating(String label, String cssClass, int votes) {
		_label = label;
		_cssClass = cssClass;
		_votes = votes;
	}
	
	/**
	 * The label to display this {@link Rating}.
	 */
	public String label() {
		return _label;
	}
	
	/**
	 * The CSS class for visualizing this rating.
	 */
	public String cssClass() {
		return _cssClass;
	}
	

	int votes() {
		int votes = 0;
		switch (this) {
		case A_LEGITIMATE: votes = -2; break;
		case B_MISSED: votes = 0; break;
		case C_PING: votes = 1; break;
		case D_POLL: votes = 1; break;
		case E_ADVERTISING: votes = 2; break;
		case F_GAMBLE: votes = 2; break;
		case G_FRAUD: votes = 4; break;
		}
		return votes;
	}

	/** 
	 * The number of votes this {@link Rating} is worth.
	 */
	public int getVotes() {
		return _votes;
	}

}
