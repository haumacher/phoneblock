/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

/**
 * The top-level {@link Resource} of the CardDAV tree.
 */
public final class RootResource extends Resource {
	
	/** 
	 * Creates a {@link RootResource}.
	 */
	public RootResource(String rootUrl, String resourcePath) {
		super(rootUrl, resourcePath);
	}

}