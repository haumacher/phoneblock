/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.haumacher.phoneblock.tools.classifier.ClassificationBatchResult.Entry;
import de.haumacher.phoneblock.tools.classifier.ClassificationBatchResult.Verdict;

/**
 * Orchestrates the classification + summarization pass:
 * <ol>
 *   <li>Collects unclassified comments from non-whitelisted phone numbers that have
 *       fewer than {@code goodThreshold} GOOD comments.</li>
 *   <li>Classifies them in mixed-number batches via Anthropic.</li>
 *   <li>For every phone number touched during classification, runs a summary request
 *       if at least {@code minGoodForSummary} GOOD comments remain.</li>
 * </ol>
 */
public class ClassifyAndSummarize {

	private static final Logger LOG = LoggerFactory.getLogger(ClassifyAndSummarize.class);

	private static final String CLASSIFY_SYSTEM = """
			Du bewertest Kommentare zu einer Telefonnummer für eine Spam-Datenbank.
			Jeder Kommentar kommt mit einem Kategorie-Label: "legitim" oder "spam".

			Markiere jeden Kommentar als "good" oder "bad" nach diesen Regeln:

			1. "good" erfordert konkrete Information über den Anrufer: eine benannte Firma/Institution, \
			   Inhalt oder Zweck des Gesprächs, beschriebene Masche, Zielgruppe, Auffälligkeiten, \
			   Nummern- oder Rückrufmuster, etc. Reine Meinungsäußerung ("nervt", "Spam", \
			   "mag ich nicht", "lästig") zählt NICHT als Information. Aussagen wie "ich habe nicht \
			   abgenommen", "kenne die Nummer nicht", "mein Handy zeigt Spam an" sind ebenfalls \
			   KEINE Information über den Anrufer. "bad".

			2. Der Text darf dem Label nicht widersprechen (z.B. Label "spam" aber Text \
			   "netter Anruf, alles in Ordnung" -> Widerspruch -> "bad").

			Nur "good", wenn Kriterium 1 erfüllt ist UND keine Widerspruchsverletzung bei 2 vorliegt. \
			Pro Eingabe-Kommentar genau ein Eintrag in der Ausgabe, mit identischer id.
			""";

	/** Map internal rating enum to the two buckets the prompt understands. */
	private static String ratingBucket(String rating) {
		return "A_LEGITIMATE".equals(rating) ? "legitim" : "spam";
	}

	private static final String SUMMARIZE_SYSTEM = """
			Du fasst Nutzerkommentare zu einer deutschen Rufnummer in höchstens 40 Wörtern \
			auf Deutsch zusammen. Die Zusammenfassung soll sagen, wer anruft und was er will. \
			Ignoriere Kommentare, die keine konkrete Information über den Anrufer enthalten. \
			Antworte nur mit der Zusammenfassung als reiner Text, ohne Präfix, ohne Anrede.
			""";

	private final ClassifierDB _db;
	private final AnthropicClient _llm;
	private final ClassifierConfig _config;
	private final ObjectMapper _json = new ObjectMapper();

	private int _requestsUsed;

	public ClassifyAndSummarize(ClassifierDB db, AnthropicClient llm, ClassifierConfig config) {
		_db = db;
		_llm = llm;
		_config = config;
	}

	public void run() throws Exception {
		LinkedHashMap<String, Integer> goodCount = new LinkedHashMap<>();
		Set<String> touchedPhones = new HashSet<>();

		long tStart = System.currentTimeMillis();
		boolean explicit = !_config.getPhones().isEmpty();
		Map<String, Iterator<PendingComment>> iterators = new LinkedHashMap<>();

		try (SqlSession session = _db.openSession()) {
			Comments mapper = session.getMapper(Comments.class);

			// Seed goodCount once for every phone that already has GOODs.
			for (PhoneCount pc : mapper.goodCountsByPhone()) {
				goodCount.put(pc.getPhone(), pc.getCount());
			}
			LOG.info("Seeded {} phones with existing GOOD counts ({} ms).",
					goodCount.size(), System.currentTimeMillis() - tStart);

			if (explicit) {
				for (String phone : _config.getPhones()) {
					if (mapper.isWhitelisted(phone) > 0) {
						LOG.warn("Skipping whitelisted phone {}.", phone);
						continue;
					}
					if (mapper.hasSummary(phone) > 0) {
						LOG.warn("Skipping {} — already has a SUMMARY row. Delete it to reprocess.", phone);
						continue;
					}
					List<PendingComment> pending = mapper.pendingForPhone(phone, 200);
					if (!pending.isEmpty()) {
						iterators.put(phone, pending.iterator());
					} else {
						int total = mapper.countAll(phone);
						if (total == 0) {
							LOG.warn("Phone {} has no COMMENTS rows at all — wrong phone-ID format? "
									+ "German numbers start with a single 0, non-German with '00<country>'.", phone);
						} else {
							LOG.info("Phone {} has no unclassified comments ({} total, {} GOOD already).",
									phone, total, goodCount.getOrDefault(phone, 0));
						}
					}
				}
				LOG.info("Processing {} phone number(s) from --phone/--phones.", iterators.size());
			} else {
				LOG.info("Fetching all eligible unclassified comments in one query...");
				List<PendingComment> all = mapper.allPendingEligible(
						_config.getGoodThreshold(), _config.getMinComments());
				LOG.info("Loaded {} unclassified comments ({} ms). Grouping by phone...",
						all.size(), System.currentTimeMillis() - tStart);
				String current = null;
				List<PendingComment> bucket = null;
				for (PendingComment c : all) {
					if (!c.getPhone().equals(current)) {
						if (bucket != null) iterators.put(current, bucket.iterator());
						current = c.getPhone();
						bucket = new ArrayList<>();
					}
					bucket.add(c);
				}
				if (bucket != null) iterators.put(current, bucket.iterator());
				LOG.info("Grouped into {} phones with unclassified comments ({} ms).",
						iterators.size(), System.currentTimeMillis() - tStart);
			}
		}

		// Classification loop — draw from rotating iterators.
		int totalClassified = 0;
		while (_requestsUsed < _config.getMaxRequests() && !iterators.isEmpty()) {
			List<PendingComment> batch = assembleBatch(iterators, goodCount);
			if (batch.isEmpty()) {
				break;
			}
			long t0 = System.currentTimeMillis();
			Map<String, Integer> results = classifyBatch(batch);
			_requestsUsed++;
			int goodInBatch = (int) results.values().stream().filter(v -> v == 1).count();
			applyResults(batch, results, goodCount, touchedPhones);
			totalClassified += batch.size();
			LOG.info("Batch {}/{}: {} comments ({} good, {} bad) in {} ms — {} phones still open, {} total classified.",
					_requestsUsed, _config.getMaxRequests(),
					batch.size(), goodInBatch, batch.size() - goodInBatch,
					System.currentTimeMillis() - t0,
					iterators.size(), totalClassified);
		}

		LOG.info("Classification pass done. LLM requests used: {}. Phones touched: {}.",
				_requestsUsed, touchedPhones.size());

		// Summarize phones touched in this run that now have enough GOOD comments.
		List<String> eligible = touchedPhones.stream()
				.filter(p -> goodCount.getOrDefault(p, 0) >= _config.getMinGoodForSummary())
				.toList();
		LOG.info("{} of {} touched phones qualify for summary (>= {} GOOD).",
				eligible.size(), touchedPhones.size(), _config.getMinGoodForSummary());
		int summarized = 0;
		for (String phone : eligible) {
			long t0 = System.currentTimeMillis();
			summarizePhone(phone);
			summarized++;
			LOG.info("Summary {}/{}: {} in {} ms.", summarized, eligible.size(), phone,
					System.currentTimeMillis() - t0);
		}
		LOG.info("Summaries written: {}. Classification LLM requests: {}. Total LLM requests: {}.",
				summarized, _requestsUsed, _requestsUsed + summarized);
	}

	private List<PendingComment> assembleBatch(Map<String, Iterator<PendingComment>> iterators,
			Map<String, Integer> goodCount) {
		List<PendingComment> batch = new ArrayList<>(_config.getBatchSize());
		Iterator<Map.Entry<String, Iterator<PendingComment>>> phones = iterators.entrySet().iterator();
		while (batch.size() < _config.getBatchSize() && phones.hasNext()) {
			Map.Entry<String, Iterator<PendingComment>> entry = phones.next();
			String phone = entry.getKey();
			if (goodCount.getOrDefault(phone, 0) >= _config.getGoodThreshold()) {
				phones.remove();
				continue;
			}
			if (!entry.getValue().hasNext()) {
				phones.remove();
				continue;
			}
			batch.add(entry.getValue().next());
		}
		return batch;
	}

	private Map<String, Integer> classifyBatch(List<PendingComment> batch) throws Exception {
		List<ClassifierInput> input = batch.stream()
				.map(c -> new ClassifierInput(c.getId(), ratingBucket(c.getRating()), nullSafe(c.getComment())))
				.toList();
		String userMessage = _json.writerWithDefaultPrettyPrinter().writeValueAsString(input);

		ClassificationBatchResult result = _llm.completeStructured(
				CLASSIFY_SYSTEM, userMessage, 1024, ClassificationBatchResult.class);

		Map<String, Integer> out = new HashMap<>();
		for (Entry entry : result.entries()) {
			out.put(entry.id(), entry.classification() == Verdict.good ? 1 : -1);
		}
		return out;
	}

	private void applyResults(List<PendingComment> batch, Map<String, Integer> results,
			Map<String, Integer> goodCount, Set<String> touched) {
		try (SqlSession session = _db.openSession()) {
			Comments mapper = session.getMapper(Comments.class);
			for (PendingComment c : batch) {
				Integer cls = results.get(c.getId());
				if (cls == null) {
					LOG.warn("No classification returned for id {}, leaving unclassified.", c.getId());
					continue;
				}
				mapper.setClassification(c.getId(), cls);
				touched.add(c.getPhone());
				if (cls == 1) {
					goodCount.merge(c.getPhone(), 1, Integer::sum);
				}
			}
			session.commit();
		}
	}

	private void summarizePhone(String phone) throws Exception {
		List<PendingComment> goods;
		try (SqlSession session = _db.openSession()) {
			goods = session.getMapper(Comments.class).goodForPhone(phone);
		}
		if (goods.size() < _config.getMinGoodForSummary()) {
			return;
		}
		StringBuilder user = new StringBuilder();
		user.append("Telefonnummer: ").append(phone).append("\nKommentare:\n");
		int budget = 7000;
		for (PendingComment c : goods) {
			String line = "- [" + c.getRating() + "] " + nullSafe(c.getComment()) + "\n";
			if (user.length() + line.length() > budget) break;
			user.append(line);
		}
		String summary = _llm.complete(SUMMARIZE_SYSTEM, user.toString(), 256).trim();
		if (summary.isEmpty()) {
			LOG.warn("Empty summary returned for {}.", phone);
			return;
		}
		try (SqlSession session = _db.openSession()) {
			session.getMapper(Comments.class).upsertSummary(phone, summary, System.currentTimeMillis());
			session.commit();
		}
		LOG.debug("Summary written for {} ({} chars).", phone, summary.length());
	}

	private static String nullSafe(String s) {
		return s == null ? "" : s;
	}

	/** Shape of one entry in the classifier's user message. */
	private record ClassifierInput(String id, String label, String text) {}
}
