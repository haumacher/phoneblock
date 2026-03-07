/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import io.nayuki.qrcodegen.QrCode;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Generates an EPC QR code (GiroCode) PNG for SEPA bank transfer donations.
 *
 * <p>The QR code encodes the bank account details from configuration and the
 * per-user supporterId as remittance text, following the EPC069-12 v002 standard.</p>
 */
@WebServlet(urlPatterns = EpcQrCodeServlet.PATH)
public class EpcQrCodeServlet extends HttpServlet {

	/**
	 * URL path for the QR code image.
	 */
	public static final String PATH = "/support-banktransfer/qr.png";

	private static final int SCALE = 8;
	private static final int BORDER = 4;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		String supporterId = "PhoneBlock-" + userName.substring(0, Math.min(userName.length(), 13));

		String receiver = UIProperties.APP_PROPERTIES.getProperty("bank.receiver", "");
		String account = UIProperties.APP_PROPERTIES.getProperty("bank.account", "");
		String bic = UIProperties.APP_PROPERTIES.getProperty("bank.bic", "");

		String epcPayload = String.join("\n",
			"BCD",      // Service tag
			"002",      // Version
			"1",        // Encoding: UTF-8
			"SCT",      // SEPA Credit Transfer
			bic,        // BIC
			receiver,   // Beneficiary name
			account,    // IBAN
			"",         // Amount (empty = user decides)
			"CHAR",     // Purpose: charity
			"",         // Creditor reference
			supporterId // Remittance text
		);

		QrCode qr = QrCode.encodeText(epcPayload, QrCode.Ecc.MEDIUM);

		int size = (qr.size + BORDER * 2) * SCALE;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int moduleX = x / SCALE - BORDER;
				int moduleY = y / SCALE - BORDER;
				boolean black = qr.getModule(moduleX, moduleY);
				image.setRGB(x, y, black ? 0x000000 : 0xFFFFFF);
			}
		}

		resp.setContentType("image/png");
		resp.setHeader("Cache-Control", "private, no-cache");
		try (OutputStream out = resp.getOutputStream()) {
			ImageIO.write(image, "png", out);
		}
	}
}
