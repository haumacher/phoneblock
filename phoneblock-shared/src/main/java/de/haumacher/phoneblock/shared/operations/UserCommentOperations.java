package de.haumacher.phoneblock.shared.operations;

import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.shared.Direction;
import de.haumacher.phoneblock.shared.Language;

public interface UserCommentOperations {
	
	UserComment self();

	default Direction getDirection() {
		return Language.fromTag(self().getLang()).direction;
	}
}
