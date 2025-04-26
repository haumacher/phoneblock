package de.haumacher.phoneblock.app.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Test case for {@link DefaultController}.
 */
public class TestDefaultController {

	/**
	 * @see DefaultController#toModel(Properties)
	 */
	@Test
	public void testToModel() {
		Properties props = new Properties();
		props.put("foo.bar", "x");
		props.put("foo.bazz", "y");
		props.put("xxx", "z");
		Map<String, Object> map = DefaultController.toModel(props);

		assertEquals("x", ((Map<?,?>) map.get("foo")).get("bar"));
		assertEquals("y", ((Map<?,?>) map.get("foo")).get("bazz"));
		assertEquals("z", map.get("xxx"));
	}
	
}
