/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

/**
 * Utility to map HTTP status codes to their standard reason phrases.
 */
public class HttpStatus {

	/**
	 * Returns the standard HTTP reason phrase for the given status code.
	 */
	public static String reasonPhrase(int statusCode) {
		switch (statusCode) {
			case 200: return "OK";
			case 201: return "Created";
			case 204: return "No Content";
			case 207: return "Multi-Status";
			case 301: return "Moved Permanently";
			case 302: return "Found";
			case 304: return "Not Modified";
			case 400: return "Bad Request";
			case 401: return "Unauthorized";
			case 403: return "Forbidden";
			case 404: return "Not Found";
			case 405: return "Method Not Allowed";
			case 409: return "Conflict";
			case 412: return "Precondition Failed";
			case 500: return "Internal Server Error";
			case 501: return "Not Implemented";
			case 503: return "Service Unavailable";
			default: return "Unknown";
		}
	}

}
