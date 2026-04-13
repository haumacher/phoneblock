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
			Du bewertest Kommentare zu einer Telefonnummer. Pro Kommentar liegen Text und ein \
			vom Nutzer vergebenes Rating vor (z.B. A_LEGITIMATE, C_PING, D_POLL, E_ADVERTISING, \
			F_GAMBLE, G_FRAUD oder B_MISSED). Markiere jeden Kommentar als "good" oder "bad" \
			nach zwei Kriterien:
			1. Er enthält konkrete Information über den Anrufer (Firma, Gesprächsinhalt, \
			   Masche, Zielgruppe, Auffälligkeiten).
			2. Die Textaussage ist mit dem Rating vereinbar (z.B. Rating=G_FRAUD, aber Text \
			   "netter Anruf, alles ok" ist ein Widerspruch -> bad).
			Nur "good", wenn beide Kriterien erfüllt sind. Pro Eingabe-Kommentar genau ein \
			Eintrag in der Ausgabe, mit identischer id.
			""";

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

		List<String> candidates;
		try (SqlSession session = _db.openSession()) {
			Comments mapper = session.getMapper(Comments.class);
			if (!_config.getPhones().isEmpty()) {
				candidates = new ArrayList<>();
				for (String phone : _config.getPhones()) {
					if (mapper.isWhitelisted(phone) > 0) {
						LOG.warn("Skipping whitelisted phone {}.", phone);
						continue;
					}
					candidates.add(phone);
				}
				LOG.info("Processing {} phone number(s) from --phone/--phones.", candidates.size());
			} else {
				candidates = mapper.candidatePhones(_config.getGoodThreshold());
				LOG.info("Found {} candidate phone numbers with unclassified comments.", candidates.size());
			}
		}

		// Initialize good counts and comment iterators for each phone.
		Map<String, Iterator<PendingComment>> iterators = new LinkedHashMap<>();
		try (SqlSession session = _db.openSession()) {
			Comments mapper = session.getMapper(Comments.class);
			for (String phone : candidates) {
				int good = mapper.countGood(phone);
				goodCount.put(phone, good);
				List<PendingComment> pending = mapper.pendingForPhone(phone, 200);
				if (!pending.isEmpty()) {
					iterators.put(phone, pending.iterator());
				}
			}
		}

		// Classification loop — draw from rotating iterators.
		while (_requestsUsed < _config.getMaxRequests() && !iterators.isEmpty()) {
			List<PendingComment> batch = assembleBatch(iterators, goodCount);
			if (batch.isEmpty()) {
				break;
			}
			Map<String, Integer> results = classifyBatch(batch);
			_requestsUsed++;
			applyResults(batch, results, goodCount, touchedPhones);
		}

		LOG.info("Classification pass done. LLM requests used: {}. Phones touched: {}.",
				_requestsUsed, touchedPhones.size());

		// Summarize phones touched in this run that now have enough GOOD comments.
		int summarized = 0;
		for (String phone : touchedPhones) {
			if (_requestsUsed >= _config.getMaxRequests()) {
				LOG.info("Max request budget hit, stopping before summarize({}).", phone);
				break;
			}
			int good = goodCount.getOrDefault(phone, 0);
			if (good < _config.getMinGoodForSummary()) {
				LOG.debug("Skipping summary for {} (only {} GOOD comments).", phone, good);
				continue;
			}
			summarizePhone(phone);
			_requestsUsed++;
			summarized++;
		}
		LOG.info("Summaries written: {}. Total LLM requests: {}.", summarized, _requestsUsed);
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
				.map(c -> new ClassifierInput(c.getId(), c.getRating(), nullSafe(c.getComment())))
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
		LOG.info("Summary written for {} ({} chars).", phone, summary.length());
	}

	private static String nullSafe(String s) {
		return s == null ? "" : s;
	}

	/** Shape of one entry in the classifier's user message. */
	private record ClassifierInput(String id, String rating, String text) {}
}
