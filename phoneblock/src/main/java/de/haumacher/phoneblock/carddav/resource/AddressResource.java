/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static de.haumacher.phoneblock.util.DomUtil.*;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.answerbot.AnswerBot;
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

	private NumberBlock _block;

	/** 
	 * Creates a {@link AddressResource}.
	 * 
	 * @param block The block of numbers represented by this resource.
	 * @param principal The user name of the address books owner.
	 */
	public AddressResource(NumberBlock block, String rootUrl, String resourcePath, String principal) {
		super(rootUrl, resourcePath);
		_block = block;
		_principal = principal;
	}
	
	public String getId() {
		return _block.getBlockId();
	}
	
	@Override
	protected QName getResourceType() {
		return CardDavSchema.CARDDAV_ADDRESS_DATA;
	}

	@Override
	public String getEtag() {
		return Integer.toHexString(_block.getBlockTitle().hashCode());
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
	public int fillProperty(HttpServletRequest req, Element propElement, QName property) {
		if (CardDavSchema.CARDDAV_ADDRESS_DATA.equals(property)) {
			Element container = appendElement(propElement, CardDavSchema.CARDDAV_ADDRESS_DATA);
			appendText(container, vCardContent());
			return HttpServletResponse.SC_OK;
		}
		return super.fillProperty(req, propElement, property);
	}

	private String vCardContent() {
		StringBuilder buffer = new StringBuilder();
		buffer.append("BEGIN:VCARD\n");
		buffer.append("VERSION:3.0\n");
		buffer.append("UID:");
		buffer.append(_block.getBlockId());
		buffer.append("\n");
		buffer.append("FN:");
		buffer.append(AnswerBot.SPAM_MARKER);
		buffer.append(" ");
		buffer.append(_block.getBlockTitle());
		buffer.append("\n");
		buffer.append("CATEGORIES:SPAM\n");
		for (String number : _block.getNumbers()) {
			buffer.append("TEL;TYPE=WORK:");
			buffer.append(number);
			buffer.append("\n");
		}
		buffer.append("END:VCARD");
		return buffer.toString();
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
			
			if (card.getTelephoneNumbers().size() > 1) {
				LOG.warn("Prevent putting card with multiple numbers: " + card);
			} else {
				for (Telephone phone : card.getTelephoneNumbers()) {
					String phoneText = phone.getText();
					
					String phoneId = NumberAnalyzer.toId(phoneText);
					if (phoneId == null) {
						continue;
					}
					
					LOG.info("Adding to block list: " + phoneId);
					
					blockList.removeExclude(currentUser, phoneId);
					
					// Safety, prevent duplicate key constraint violation.
					blockList.removePersonalization(currentUser, phoneId);
					
					blockList.addPersonalization(currentUser, phoneId);
					db.processVotesAndPublish(spamreport, phoneId, 2, System.currentTimeMillis());
				}
				
				session.commit();
				
				// Ensure that the new number is added to the user's address book immediately.
				AddressBookCache.getInstance().flushUserCache(_principal);
			}
		}
		
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

	@Override
	public void delete(HttpServletResponse resp) {
		// Cannot allow to delete a potential block of numbers.
		LOG.warn("Prevent deleting card: " + _block.getBlockTitle());
		
		resp.setStatus(HttpServletResponse.SC_OK);
	}
}
