package de.haumacher.phoneblock.analysis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.WebCSVTable.ColumnMapping;
import de.haumacher.phoneblock.analysis.WebCSVTable.Replacement;

/**
 * Test cases for {@link WebCSVTable} replacement functionality.
 */
public class WebCSVTableTest {

	@Test
	public void testParseColumnSpecWithoutMapping() {
		ColumnMapping mapping = WebCSVTable.parseColumnSpec("Country");
		assertEquals("Country", mapping.pageColumnName);
		assertEquals("Country", mapping.csvColumnName);
	}

	@Test
	public void testParseColumnSpecWithMapping() {
		ColumnMapping mapping = WebCSVTable.parseColumnSpec("Country Name=Country");
		assertEquals("Country Name", mapping.pageColumnName);
		assertEquals("Country", mapping.csvColumnName);
	}

	@Test
	public void testParseColumnSpecWithMappingAndSpaces() {
		ColumnMapping mapping = WebCSVTable.parseColumnSpec(" Country Name = Country ");
		assertEquals("Country Name", mapping.pageColumnName);
		assertEquals("Country", mapping.csvColumnName);
	}

	@Test
	public void testParseColumnSpecWithEmptyCSVName() {
		ColumnMapping mapping = WebCSVTable.parseColumnSpec("PageCol=");
		assertEquals("PageCol", mapping.pageColumnName);
		assertEquals("", mapping.csvColumnName);
	}


	@Test
	public void testBasicReplacement() {
		Replacement r = WebCSVTable.parseReplacement("s/old/new/");
		assertEquals("new", r.apply("old"));
		assertEquals("new text", r.apply("old text"));
		assertEquals("text new", r.apply("text old")); // Only first occurrence
		assertEquals("new old", r.apply("old old")); // Only first occurrence
	}

	@Test
	public void testGlobalReplacement() {
		Replacement r = WebCSVTable.parseReplacement("s/old/new/g");
		assertEquals("new new", r.apply("old old"));
		assertEquals("new text new", r.apply("old text old"));
	}

	@Test
	public void testCaseInsensitiveReplacement() {
		Replacement r = WebCSVTable.parseReplacement("s/old/new/i");
		assertEquals("new", r.apply("OLD"));
		assertEquals("new", r.apply("Old"));
		assertEquals("new", r.apply("old"));
	}

	@Test
	public void testGlobalCaseInsensitiveReplacement() {
		Replacement r = WebCSVTable.parseReplacement("s/old/new/gi");
		assertEquals("new new new", r.apply("old OLD Old"));
	}

	@Test
	public void testAlternativeDelimiterPipe() {
		Replacement r = WebCSVTable.parseReplacement("s|old|new|");
		assertEquals("new", r.apply("old"));
	}

	@Test
	public void testAlternativeDelimiterHash() {
		Replacement r = WebCSVTable.parseReplacement("s#old#new#");
		assertEquals("new", r.apply("old"));
	}

	@Test
	public void testDelimiterInPatternWithAlternativeDelimiter() {
		// Test pattern containing / when using | as delimiter
		Replacement r = WebCSVTable.parseReplacement("s|/old|/new|");
		assertEquals("/new", r.apply("/old"));
	}

	@Test
	public void testEmptyReplacement() {
		// Remove commas
		Replacement r = WebCSVTable.parseReplacement("s/,//g");
		assertEquals("1000", r.apply("1,000"));
		assertEquals("1000000", r.apply("1,000,000"));
	}

	@Test
	public void testRegexPattern() {
		// Test actual regex pattern matching
		Replacement r = WebCSVTable.parseReplacement("s/\\d+/NUMBER/g");
		assertEquals("NUMBER", r.apply("123"));
		assertEquals("NUMBER and NUMBER", r.apply("123 and 456"));
	}

	@Test
	public void testWhitespaceReplacement() {
		Replacement r = WebCSVTable.parseReplacement("s/\\s+/ /g");
		assertEquals("one two three", r.apply("one  two   three"));
	}

	@Test
	public void testReplacementWithSpecialCharacters() {
		Replacement r = WebCSVTable.parseReplacement("s/Republic/Rep./g");
		assertEquals("Rep. of Korea", r.apply("Republic of Korea"));
		assertEquals("Rep.", r.apply("Republic"));
	}

	@Test
	public void testMultiWordReplacement() {
		Replacement r = WebCSVTable.parseReplacement("s|United States|USA|");
		assertEquals("USA", r.apply("United States"));
		assertEquals("USA of America", r.apply("United States of America"));
	}

	@Test
	public void testInvalidSpecificationNoDelimiter() {
		Exception exception = assertThrows(IllegalArgumentException.class, () -> {
			WebCSVTable.parseReplacement("invalid");
		});
		assertTrue(exception.getMessage().contains("Replacement must start with 's'"));
	}

	@Test
	public void testInvalidSpecificationMissingDelimiter() {
		Exception exception = assertThrows(IllegalArgumentException.class, () -> {
			WebCSVTable.parseReplacement("s/pattern");
		});
		assertTrue(exception.getMessage().contains("Missing replacement delimiter"));
	}

	@Test
	public void testInvalidSpecificationMissingFinalDelimiter() {
		Exception exception = assertThrows(IllegalArgumentException.class, () -> {
			WebCSVTable.parseReplacement("s/pattern/replacement");
		});
		assertTrue(exception.getMessage().contains("Missing final delimiter"));
	}

	@Test
	public void testEscapedDelimiter() {
		// Test escaped delimiter in pattern
		Replacement r = WebCSVTable.parseReplacement("s/a\\/b/c/");
		assertEquals("c", r.apply("a/b"));
	}

	@Test
	public void testChainedReplacements() {
		// Simulate applying multiple replacements to same value
		Replacement r1 = WebCSVTable.parseReplacement("s/Republic/Rep./g");
		Replacement r2 = WebCSVTable.parseReplacement("s/United States/USA/");

		String value = "United States Republic";
		value = r2.apply(value);
		value = r1.apply(value);

		assertEquals("USA Rep.", value);
	}
}
