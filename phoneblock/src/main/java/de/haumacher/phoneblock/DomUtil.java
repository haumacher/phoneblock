/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class DomUtil {

	static final DocumentBuilder documentBuilder;
	
	static {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		try {
			documentBuilder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * TODO
	 */
	public static DocumentBuilder getBuilder() {
		return documentBuilder;
	}

	public static void appendTextElement(Element response, QName qname, String text) {
		appendTextElement(response, qname.getNamespaceURI(), qname.getLocalPart(), text);
	}
	
	public static void appendTextElement(Element response, String nsUri, String localName, String text) {
		appendText(appendElement(response, nsUri, localName), text);
	}

	public static void appendText(Element element, String string) {
		element.appendChild(element.getOwnerDocument().createTextNode(string));
	}

	public static Element appendElement(Node parent, QName qname) {
		return appendElement(parent, qname.getNamespaceURI(), qname.getLocalPart());
	}
	
	public static Element appendElement(Node parent, String nsUri, String localName) {
		Element element = ownerDocument(parent).createElementNS(nsUri, localName);
		parent.appendChild(element);
		return element;
	}

	public static Document ownerDocument(Node parent) {
		return parent.getNodeType() == Node.DOCUMENT_NODE ? (Document) parent : parent.getOwnerDocument();
	}

	public static Iterable<Element> elements(Node root, QName... names) {
		Iterable<Element> children = elements(root);
		for (QName name : names) {
			children = elements(filter(children, name));
		}
		return children;
	}

	public static Iterable<Element> elements(Iterable<Element> base) {
		return new Iterable<Element>() {
			@Override
			public Iterator<Element> iterator() {
				return new Iterator<Element>() {
					Iterator<Element> _roots = base.iterator();
					Iterator<Element> _current;
					Element _candidate;
					
					@Override
					public boolean hasNext() {
						if (_candidate != null) {
							return true;
						}
	
						while (true) {
							if (_current != null && _current.hasNext()) {
								_candidate = _current.next();
								return true;
							}
							
							if (_roots.hasNext()) {
								_current = elements(_roots.next()).iterator();
							} else {
								return false;
							}
						}
					}
	
					@Override
					public Element next() {
						if (!hasNext()) {
							throw new NoSuchElementException();
						}
						Element result = _candidate;
						_candidate = null;
						return result;
					}
				};
			}
		};
	}

	public static Iterable<Element> filter(Iterable<Element> children, QName name) {
		return new Iterable<Element>() {
			@Override
			public Iterator<Element> iterator() {
				return new Iterator<Element>() {
					Iterator<Element> _base = children.iterator();
					
					Element _candidate; 
					
					@Override
					public boolean hasNext() {
						if (_candidate != null) {
							return true;
						}
						while (_base.hasNext()) {
							Element next = _base.next();
							if (hasName(name, next)) {
								_candidate = next;
								return true;
							}
						}
						return false;
					}
	
					@Override
					public Element next() {
						if (!hasNext()) {
							throw new NoSuchElementException();
						}
						Element result = _candidate;
						_candidate = null;
						return result;
					}
				};
			}
		};
	}

	public static boolean hasName(QName name, Element next) {
		return name.getLocalPart().equals(next.getLocalName()) && eq(name.getNamespaceURI(), next.getNamespaceURI());
	}

	public static boolean eq(String s1, String s2) {
		return s1 == null ? s2 == null : s1.equals(s2);
	}

	public static Iterable<Element> elements(Node root) {
		return new Iterable<Element>() {
			@Override
			public Iterator<Element> iterator() {
				return new Iterator<Element>() {
					Node _child = root.getFirstChild();
					
					@Override
					public boolean hasNext() {
						while (_child != null) {
							if (_child.getNodeType() == Node.ELEMENT_NODE) {
								return true;
							}
							
							_child = _child.getNextSibling();
						}
						return false;
					}
					
					@Override
					public Element next() {
						if (!hasNext()) {
							throw new NoSuchElementException();
						}
						Element result = (Element) _child;
						_child = _child.getNextSibling();
						return result;
					}
				};
			}
		};
	}

	public static QName qname(Element element) {
		return qname(element.getNamespaceURI(), element.getLocalName());
	}
	
	public static QName qname(String namespaceURI, String localPart) {
		return new QName(namespaceURI, localPart);
	}

	public static <T> List<T> toList(Iterable<T> elements) {
		ArrayList<T> result = new ArrayList<T>();
		for (T elem : elements) {
			result.add(elem);
		}
		return result;
	}

	public static List<QName> qnames(Iterable<Element> elements) {
		ArrayList<QName> result = new ArrayList<>();
		for (Element element : elements) {
			result.add(qname(element));
		}
		return result;
	}

}
