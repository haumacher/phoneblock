package de.haumacher.phoneblock.credits;

import java.io.IOException;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

public class MailParser {

	public MailParser() {
		super();
	}
	
	public MessageDetails parse(Message message) throws IOException, MessagingException {
		try {
			return tryParse(message);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid message (" + message.getReceivedDate() + "): " + e.getMessage() + "\n" + message.getContent());
		}
	}
	
	private MessageDetails tryParse(Message message) throws MessagingException, IOException, ParseException {
		// X-DKIM-Status: pass [(paypal.de) - 173.0.84.1]
		String S = "\\s*";
		String passPattern = "\\w+";
		String domainPattern = "[^\\)]*";
		String ipPattern = "[^\\]]*";
		Pattern statusPattern = Pattern.compile("(" + passPattern + ")" + S + "\\[" + S + "\\(" + "(" + domainPattern + ")" + "\\)" + S + "-" + S + "(" + ipPattern + ")" + "\\]" + S);

		boolean dkimSuccess = false;
		String[] dkimStatusHeaders = message.getHeader("X-DKIM-Status");
		for (String dkimStatus : dkimStatusHeaders) {
			Matcher matcher = statusPattern.matcher(dkimStatus);
			if (!matcher.matches()) {
				continue;
			}
			
			String status = matcher.group(1);
			String domain = matcher.group(2);
			
			if ("pass".equals(status) && "paypal.de".equals(domain)) {
				dkimSuccess = true;
				break;
			}
		}
		
		if (!dkimSuccess) {
			throw new IllegalArgumentException("No DKIM signature.");
		}

		
		boolean senderSuccess = false;
		for (Address from : message.getFrom()) {
			if (from instanceof InternetAddress email) {
				String address = email.getAddress();
				if ("service@paypal.de".equals(address)) {
					senderSuccess = true;
					break;
				}
			}
		}
		
		if (!senderSuccess) {
			throw new IllegalArgumentException("Invalid sender.");
		}
		
		Object content = message.getContent();
		
		Document document = Jsoup.parse((String) content);

		String titleElement = document.selectXpath("/html/body/h4[1]").text();
		Elements tables = document.selectXpath("/html/body/table[1]/tbody[1]/tr[1]/td[@class='mobContent']/table[2]/tbody[1]/tr[1]/td[1]/table");
		
		int tableCnt = tables.size();

		if (tableCnt < 12) {
			throw new IllegalArgumentException("Invalid message format: tables=" + tableCnt);
		}
		
		boolean hasMessage = tableCnt >= 14;
		
		String amountElement = tables.get(0).selectXpath("tbody[1]/tr[1]/td[1]/p[@class='ppsans']/span[1]").text();
		if (amountElement.isEmpty()) {
			throw new IllegalArgumentException("No amount given.");
		}
		
		String msg = hasMessage ? tables.get(2).selectXpath("tbody[1]/tr[1]/td[1]/table[1]/tbody[1]/tr[1]/td[2]/p[1]/span[1]").text() : "";
		
		Element detailsTable = tables.get(hasMessage ? 4 : 2);
		
		String tx = detailsTable.selectXpath("tbody[1]/tr[1]/td[1]/table[1]/tbody[1]/tr[1]/td[1]/a[1]/span[1]").text();
		String dateElement = detailsTable.selectXpath("tbody[1]/tr[1]/td[1]/table[1]/tbody[1]/tr[1]/td[2]/span[2]").text();
		
		if (tx.isEmpty()) {
			throw new IllegalArgumentException("No transaction found.");
		}

		if (dateElement.isEmpty()) {
			throw new IllegalArgumentException("No date found.");
		}
		
		int amount = parseAmount(Pattern.compile(".*, Du hast ([\\d]+,[\\d]+) .* erhalten"), titleElement);
		int amount2 = parseAmount(Pattern.compile(".* hat dir ([\\d]+,[\\d]+) .* gesendet"), amountElement);
		
		if (amount2 != amount || amount < 0) {
			throw new IllegalArgumentException("Inconsistent amount found: " + amount + " vs. " + amount2);
		}
		
		Date date = new SimpleDateFormat("dd. MMMM yyyy", DateFormatSymbols.getInstance(Locale.GERMAN)).parse(dateElement);
		if (date == null) {
			throw new IllegalArgumentException("No date found.");
		}
		
		String sender = parseText(Pattern.compile("(.*) hat dir .*"), amountElement);
		if (sender == null) {
			throw new IllegalArgumentException("No sender found.");
		}
		
		String uid = parseText(Pattern.compile(".*PhoneBlock-([a-f0-9]{8}-[a-f0-9]{4}).*"), msg);
		if (uid == null) {
			// b09e5208-4d83-4810-8b81-445852fb1fa5
			uid = parseText(Pattern.compile(".*([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}).*"), msg);
			if (uid == null) {
				// b09e5208-4d83-4810
				uid = parseText(Pattern.compile(".*([a-f0-9]{8}-[a-f0-9]{4}).*"), msg);
				if (uid == null) {
					// b09e5
					uid = parseText(Pattern.compile("([a-f0-9]{5,})"), msg);
				}
			}
		}
		
		return new MessageDetails(msg, tx, amount, date, sender, uid);
	}

	private static int parseAmount(Pattern pattern, String value) throws ParseException {
		String text = parseText(pattern, value);
		if (text == null) {
			return -1;
		}
		return (int) (new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.GERMAN)).parse(text).doubleValue() * 100);
	}

	private static String parseText(Pattern pattern, String value) {
		Matcher titleMatcher = pattern.matcher(value);
		String text;
		if (titleMatcher.matches()) {
			text = titleMatcher.group(1);
		} else {
			text = null;
		}
		return text;
	}
}
