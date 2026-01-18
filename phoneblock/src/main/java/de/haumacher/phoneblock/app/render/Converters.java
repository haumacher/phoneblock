package de.haumacher.phoneblock.app.render;

import java.util.Date;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;

public class Converters {
	
	public Date fromEpoch(Object input) {
		long millis = ((Number) input).longValue();
		return new Date(millis);
	}

	public Long toEpoch(Object input) {
		long millis = ((Date) input).getTime();
		return Long.valueOf(millis);
	}
	
	public boolean isPositive(Rating rating) {
		return rating == Rating.A_LEGITIMATE;
	}
	
	public PhoneNumer analyze(String phoneId) {
		PhoneNumer result = NumberAnalyzer.analyze(phoneId);
		if (result == null) {
			// Must not return null for rendering.
			return PhoneNumer.create().setId(phoneId).setPlus(phoneId).setShortcut(phoneId).setZeroZero(phoneId).setDial(phoneId);
		}
		return result;
	}
}
