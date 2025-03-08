package de.haumacher.phoneblock.app.render;

import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.Ratings;

public class RatingDisplay {

	private Rating _rating;

	public RatingDisplay(Rating rating) {
		this._rating = rating;
	}
	
	public String getLabel() {
		return de.haumacher.phoneblock.db.Ratings.getLabel(_rating);
	}
	
	public String getCssClass() {
		return Ratings.getCssClass(_rating);
	}
	
	public String getButtonClass() {
		return getLabel();
	}

	public String getIconClass() {
		return switch (_rating) {
		case A_LEGITIMATE -> "fa-solid fa-check";
		case B_MISSED -> "fa-solid fa-circle-question";
		case C_PING -> "fa-solid fa-table-tennis-paddle-ball";
		case D_POLL -> "fa-solid fa-person-chalkboard";
		case E_ADVERTISING -> "fa-solid fa-ban";
		case F_GAMBLE -> "fa-solid fa-dice";
		case G_FRAUD -> "fa-solid fa-bomb";
		};
	}
	
	@Override
	public String toString() {
		return _rating.name();
	}

}
