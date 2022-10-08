/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static de.haumacher.phoneblock.util.DomUtil.*;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.io.text.VCardReader;
import ezvcard.property.Telephone;

/**
 * {@link Resource} representing an individual entry in an {@link AddressBookResource}.
 */
public class AddressResource extends Resource {

	private static final Logger LOG = LoggerFactory.getLogger(AddressResource.class);

	private String _principal;

	/** 
	 * Creates a {@link AddressResource}.
	 * 
	 * @param principal The user name of the address books owner.
	 */
	public AddressResource(String rootUrl, String resourcePath, String principal) {
		super(rootUrl, resourcePath);
		_principal = principal;
	}
	
	@Override
	protected QName getResourceType() {
		return CardDavSchema.CARDDAV_ADDRESS_DATA;
	}

	@Override
	public String getEtag() {
		// Address entries never change. The consist only of the bare phone number. 
		return "2";
	}
	
	@Override
	public void get(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("text/x-vcard");
		resp.setCharacterEncoding("utf-8");
		resp.setHeader("ETag", quote(getEtag()));
		
		resp.getWriter().append(vCardContent());
	}
	
	@Override
	public int fillProperty(HttpServletRequest req, Element propElement, Element propertyElement, QName property) {
		if (CardDavSchema.CARDDAV_ADDRESS_DATA.equals(property)) {
			Element container = appendElement(propElement, CardDavSchema.CARDDAV_ADDRESS_DATA);
			appendText(container, vCardContent());
			return HttpServletResponse.SC_OK;
		}
		return super.fillProperty(req, propElement, propertyElement, property);
	}

	private String vCardContent() {
		String displayName = getDisplayName();
		
		return "BEGIN:VCARD\n"
			+ "VERSION:3.0\n"
			+ "UID:" + displayName + "\n"
			+ "FN:SPAM: " + displayName + "\n"
			+ "CATEGORIES:SPAM" + "\n"
			+ "TEL;TYPE=WORK:" + displayName + "\n"
			+ "END:VCARD";
	}
	
	@Override
	public void put(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		VCardReader reader = new VCardReader(req.getReader(), VCardVersion.V3_0);
		VCard card = reader.readNext();
		if (card == null) {
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports spamreport = session.getMapper(SpamReports.class);
			BlockList blockList = session.getMapper(BlockList.class);
			Users users = session.getMapper(Users.class);
			
			long currentUser = users.getUserId(_principal);
			
			for (Telephone phone : card.getTelephoneNumbers()) {
				String phoneNumber = normalizeNumber(phone.getText());
				
				LOG.info("Adding to block list: " + phoneNumber);
				
				blockList.removeExclude(currentUser, phoneNumber);
				
				// Safety, prevent duplicate key constraint violation.
				blockList.removePersonalization(currentUser, phoneNumber);
				
				blockList.addPersonalization(currentUser, phoneNumber);
				db.processVotes(spamreport, phoneNumber, 2, System.currentTimeMillis());
			}
			
			session.commit();
		}
		
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

	/** 
	 * Removes grouping characters from the given phone number.
	 */
	public static String normalizeNumber(String phoneNumber) {
		return phoneNumber.replaceAll("[- ]", "");
	}
	
	@Override
	public void delete(HttpServletResponse resp) {
		String phoneNumber = getDisplayName();
		
		DBService.getInstance().deleteEntry(_principal, phoneNumber);
		
		resp.setStatus(HttpServletResponse.SC_OK);
	}
}
