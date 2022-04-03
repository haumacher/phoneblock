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
import org.w3c.dom.Element;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.io.text.VCardReader;
import ezvcard.property.Telephone;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class AddressResource extends Resource {

	/** 
	 * Creates a {@link AddressResource}.
	 */
	public AddressResource(String rootUrl, String resourcePath) {
		super(rootUrl, resourcePath);
	}
	
	@Override
	protected QName getResourceType() {
		return CardDavSchema.CARDDAV_ADDRESS_DATA;
	}

	@Override
	protected String getEtag() {
		return "1";
	}
	
	@Override
	protected int fillProperty(HttpServletRequest req, Element propElement, Element propertyElement, QName property) {
		if (CardDavSchema.CARDDAV_ADDRESS_DATA.equals(property)) {
			String phoneNumber = getDisplayName();
			
			Element container = appendElement(propElement, property);
			appendText(container, 
				"BEGIN:VCARD\n"
				+ "VERSION:3.0\n"
				+ "UID:" + phoneNumber + "\n"
				+ "FN:SPAM: " + phoneNumber + "\n"
				+ "TEL;TYPE=WORK:" + phoneNumber + "\n"
				+ "END:VCARD");
			return HttpServletResponse.SC_OK;
		}
		return super.fillProperty(req, propElement, propertyElement, property);
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
			
			for (Telephone phone : card.getTelephoneNumbers()) {
				String phoneNumber = phone.getText();
				
				System.out.println("Adding to block list: " + phoneNumber);
				
				blockList.removeExclude(1, phoneNumber);
				blockList.addPersonalization(1, phoneNumber);
				db.processVote(spamreport, phoneNumber, 2, System.currentTimeMillis());
			}
			
			session.commit();
		}
		
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
	
	@Override
	public void delete(HttpServletResponse resp) {
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports spamreport = session.getMapper(SpamReports.class);
			BlockList blockList = session.getMapper(BlockList.class);
			
			String phoneNumber = getDisplayName();
			blockList.removePersonalization(1, phoneNumber);
			blockList.addExclude(1, phoneNumber);
			db.processVote(spamreport, phoneNumber, -2, System.currentTimeMillis());
			
			session.commit();
		}
		
		resp.setStatus(HttpServletResponse.SC_OK);
	}
}
