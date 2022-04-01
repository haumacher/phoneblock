/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.importer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.haumacher.phoneblock.util.DomUtil;

/**
 * Algorithm importing the numbers from a FritzBox exported block list.
 */
public class FritzBoxImporter {
	
	public static void main(String[] args) throws SAXException, IOException, XPathExpressionException {
		Document document = DomUtil.getBuilder().parse(new File(args[0]));
		NodeList list = (NodeList) XPathFactory.newInstance().newXPath().evaluate("//number/text()", document, XPathConstants.NODESET);
		
		Set<String> numbers = new HashSet<>();
		for (int n = 0, cnt = list.getLength(); n < cnt; n++) {
			numbers.add(list.item(n).getTextContent());
		}
		
		List<String> sortedNumbers = new ArrayList<>(numbers);
		Collections.sort(sortedNumbers);
		for (String number : sortedNumbers) {
			System.out.println(number);
		}
	}

}
