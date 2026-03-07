# EPC QR Code for Donation Page Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an EPC QR code (GiroCode) to the `/support-banktransfer` page so users can scan it with their banking app to initiate a SEPA donation.

**Architecture:** New `EpcQrCodeServlet` generates a PNG QR code on the fly using the Nayuki qrcodegen library. The QR code encodes the EPC069-12 payload with bank details from `phoneblock.properties` and the per-user `supporterId`. The template gets an `<img>` tag pointing to the servlet.

**Tech Stack:** Java 17, Jakarta Servlets, Nayuki qrcodegen 1.8.0, Thymeleaf

---

### Task 1: Add Nayuki qrcodegen Maven dependency

**Files:**
- Modify: `phoneblock/pom.xml` (after jsoup dependency, ~line 214)

**Step 1: Add dependency**

Insert after the jsoup `</dependency>` closing tag (line 214):

```xml
		<dependency>
			<groupId>io.nayuki</groupId>
			<artifactId>qrcodegen</artifactId>
			<version>1.8.0</version>
		</dependency>
```

**Step 2: Verify it resolves**

Run: `cd phoneblock && mvn dependency:resolve -pl . -q`
Expected: No errors

**Step 3: Commit**

```bash
git add phoneblock/pom.xml
git commit -m "feat(#261): add nayuki qrcodegen dependency for EPC QR codes"
```

---

### Task 2: Create EpcQrCodeServlet

**Files:**
- Create: `phoneblock/src/main/java/de/haumacher/phoneblock/app/EpcQrCodeServlet.java`

**Step 1: Write the servlet**

```java
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
```

Key implementation notes:
- Uses `LoginFilter.getAuthenticatedUser()` for auth (same pattern as `AssignContributionServlet`)
- Bank properties from `UIProperties.APP_PROPERTIES` (same source as template)
- `supporterId` computed identically to `DefaultController` line 314
- Error correction level M (MEDIUM) as required by EPC069-12 spec
- URL ends in `.png`, so `ContentFilter.requestLogin()` line 168 skips POW for it
- URL starts with `/support-banktransfer/`, so `ContentFilter.render()` won't match any excluded path prefix — but since it's a `@WebServlet`, it must be added to the excluded paths list

**Step 2: Add the servlet path to ContentFilter's excluded paths**

In `phoneblock/src/main/java/de/haumacher/phoneblock/app/render/ContentFilter.java`, the `render()` method (line 299) has a list of path prefixes that are forwarded to the servlet chain instead of being rendered as templates. Add `EpcQrCodeServlet.PATH` to this list.

In the block starting at line 310, add:
```java
			path.startsWith(EpcQrCodeServlet.PATH) ||
```

Also add the import:
```java
import de.haumacher.phoneblock.app.EpcQrCodeServlet;
```

**Step 3: Build and verify**

Run: `cd phoneblock && mvn compile -q`
Expected: No errors

**Step 4: Commit**

```bash
git add phoneblock/src/main/java/de/haumacher/phoneblock/app/EpcQrCodeServlet.java
git add phoneblock/src/main/java/de/haumacher/phoneblock/app/render/ContentFilter.java
git commit -m "feat(#261): add EPC QR code servlet for bank transfer donations"
```

---

### Task 3: Add QR code image to bank transfer template

**Files:**
- Modify: `phoneblock/src/main/webapp/WEB-INF/templates/de/support-banktransfer.html`

**Step 1: Add the QR code section**

After the `</ul>` tag (line 26) and before the closing thank-you paragraph (line 28), insert:

```html
		<p>
			Scanne den QR-Code mit Deiner Banking-App:
		</p>

		<figure class="image" style="max-width: 280px;">
			<img th:src="@{/support-banktransfer/qr.png}" alt="EPC QR-Code für Banküberweisung" />
		</figure>
```

Notes:
- No `data-tx` attribute on the new `<p>` — the i18n build tool will assign one automatically
- Uses Thymeleaf `@{...}` URL syntax for proper context path resolution
- `max-width: 280px` keeps the QR code at a scannable size without dominating the page

**Step 2: Commit**

```bash
git add phoneblock/src/main/webapp/WEB-INF/templates/de/support-banktransfer.html
git commit -m "feat(#261): display EPC QR code on bank transfer donation page"
```

---

### Task 4: Manual end-to-end test

**Step 1: Start the web application**

Run: `cd phoneblock && mvn jetty:run`

**Step 2: Test the page**

1. Open `http://localhost:8080/phoneblock/support-banktransfer` in a browser
2. Verify it redirects to login (page requires authentication)
3. Log in and navigate to `/support-banktransfer`
4. Verify the QR code image is displayed below the bank details
5. Scan the QR code with a banking app — verify it opens a SEPA transfer pre-filled with the correct IBAN, BIC, receiver, and supporterId

**Step 3: Test unauthorized access**

Open `http://localhost:8080/phoneblock/support-banktransfer/qr.png` without being logged in — should return 401.
