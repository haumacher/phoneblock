package de.haumacher.phoneblock.carddav.resource;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for {@link AddressBookCache}.
 */
public class TestAddressBookCache {

	private SchedulerService _scheduler;
	private DB _db;
	private AddressBookCache _cache;

	long _now = 0;
	

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);
		
		_db = new DB(TestDB.createTestDataSource(), _scheduler);
		
		_db.processVotes(NumberAnalyzer.analyze("+39111111111", "+49"), "+39", 20, _now);
		_db.processVotes(NumberAnalyzer.analyze("+39222222222", "+49"), "+39", 2, _now);
		_db.processVotes(NumberAnalyzer.analyze("+49333333333", "+49"), "+39", 20, _now);
		_db.processVotes(NumberAnalyzer.analyze("+49444444444", "+49"), "+39", 2, _now);
		
		_cache = new AddressBookCache(() -> _db);
	}
	
	@AfterEach
	public void tearDown() throws Exception {
		_cache = null;

		_db.shutdown();
		_db = null;
		
		_scheduler.contextDestroyed(null);
		_scheduler = null;
	}

	@Test
	public void testLookupNational() {
		_db.createUser("u1", "u1", "fr", "+39");
		UserSettings settings = _db.getSettings("u1");
		settings.setNationalOnly(true);
		settings.setMinVotes(2);
		_db.updateSettings(settings);

		List<NumberBlock> numbers = _cache.loadNumbers("u1", _now);
		Assertions.assertEquals(2, numbers.size());
		Assertions.assertEquals(Arrays.asList("+39111111111"), numbers.get(0).getNumbers());
		Assertions.assertEquals(Arrays.asList("+39222222222"), numbers.get(1).getNumbers());
	}
	
	@Test
	public void testLookupAll() {
		_db.createUser("u1", "u1", "fr", "+39");
		UserSettings settings = _db.getSettings("u1");
		settings.setNationalOnly(false);
		settings.setMinVotes(2);
		_db.updateSettings(settings);
		
		List<NumberBlock> numbers = _cache.loadNumbers("u1", _now);
		Assertions.assertEquals(Arrays.asList("+39111111111", "+39222222222", "+49333333333", "+49444444444"), phoneNumbers(numbers));
	}
	
	@Test
	public void testLookupFiltered() {
		_db.createUser("u1", "u1", "fr", "+39");
		UserSettings settings = _db.getSettings("u1");
		settings.setNationalOnly(false);
		settings.setMinVotes(10);
		_db.updateSettings(settings);
		
		List<NumberBlock> numbers = _cache.loadNumbers("u1", _now);
		Assertions.assertEquals(Arrays.asList("+39111111111", "+49333333333"), phoneNumbers(numbers));
	}
	
	@Test
	public void testLookupPersonalized() {
		_db.createUser("u1", "u1", "fr", "+39");
		UserSettings settings = _db.getSettings("u1");
		settings.setNationalOnly(false);
		settings.setMinVotes(2);
		_db.updateSettings(settings);

		_db.addRating("u1", NumberAnalyzer.analyze("+39111111111", "+49"), "+39", Rating.A_LEGITIMATE, null, "fr", _now);
		_db.addRating("u1", NumberAnalyzer.analyze("+39555555555", "+49"), "+39", Rating.E_ADVERTISING, null, "fr", _now);
		_db.addRating("u1", NumberAnalyzer.analyze("+49666666666", "+49"), "+39", Rating.F_GAMBLE, null, "fr", _now);
		
		List<NumberBlock> numbers = _cache.loadNumbers("u1", _now);
		Assertions.assertEquals(Arrays.asList("+39222222222", "+39555555555", "+49333333333", "+49444444444", "+49666666666"), phoneNumbers(numbers));
	}

	private List<String> phoneNumbers(List<NumberBlock> numbers) {
		return numbers.stream().flatMap(b -> b.getNumbers().stream()).toList();
	}
	
}
