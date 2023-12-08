/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.chatgpt;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.meta.MetaSearchService;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Service creating summary texts for phone numbers by asking ChatGPT to create a summary of user comments.
 */
public class ChatGPTService implements ServletContextListener {

	private static final int INITIAL_DELAY_SECONDS = 20;

	private static final int MAX_QUESTION_LENGTH = 8400;

	private static final Logger LOG = LoggerFactory.getLogger(MetaSearchService.class);

	private static final long TEN_MINUTE_SECONDS = Duration.ofMinutes(10).toSeconds();

	private DBService _db;
	private SchedulerService _scheduler;
	private IndexUpdateService _indexer;

	private ScheduledFuture<?> _heartBeat;

	private ScheduledFuture<?> _process;

	private OpenAiService _openai;
	
	/**
	 * Delay upon error.
	 */
	private long _delaySeconds = INITIAL_DELAY_SECONDS;

	/** 
	 * Creates a {@link ChatGPTService}.
	 */
	public ChatGPTService(DBService db, SchedulerService scheduler, IndexUpdateService indexer) {
		_db = db;
		_scheduler = scheduler;
		_indexer = indexer;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			Properties properties = new Properties();
			properties.load(ChatGPTService.class.getResourceAsStream("/phoneblock.properties"));
			String apiKey = properties.getProperty("chatgpt.secret");
			
			_openai = new OpenAiService(apiKey, Duration.ofMinutes(2));
		} catch (IOException ex) {
			LOG.error("Cannot start ChatGPTService.", ex);
			return;
		}
		
		LOG.info("Starting ChatGPTService.");
		
		_heartBeat = _scheduler.executor().scheduleWithFixedDelay(this::heardBeat, 15, 3600, TimeUnit.SECONDS);
		reschedule();
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_heartBeat != null) {
			_heartBeat.cancel(false);
			_heartBeat = null;
		}
		
		LOG.info("Stopped ChatGPTService.");
	}
	
	private void heardBeat() {
		if (_process == null || _process.isDone()) {
			LOG.warn("Processor terminated, rescheduling.");
			reschedule();
		}
		
		DB db = _db.db();
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			int cnt = reports.scheduleSummaryRequests();
			if (cnt > 0) {
				session.commit();
				
				LOG.info("Created " + cnt + " new summary requests.");
			}
		}
		
		LOG.info("ChatGPTService alive.");
	}

	private void process() {
		try {
			doProcess();
		} catch (OpenAiHttpException ex) {
			LOG.warn("ChatGPT reported error, statusCode: " + ex.statusCode + ", code: " + ex.code + ", param: " + ex.param + ": " + ex.getMessage());
			exponentialBackoff();
		} catch (SocketTimeoutException ex) {
			LOG.warn("ChatGPT request timed out: " + ex.getMessage());
			exponentialBackoff();
		} catch (Throwable ex) {
			LOG.error("Processing summary request faild: " + ex.getMessage(), ex);
			exponentialBackoff();
		}
	}
	
	/**
	 * Processes the next summary request.
	 *
	 * @throws Throwable If communication or anything else fails.
	 */
	private void doProcess() throws Throwable {
		String phone;
		List<String> comments;
		
		DB db = _db.db();
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			phone = reports.topSummaryRequest();
			if (phone == null) {
				LOG.info("No summary requests.");
				exponentialBackoff();
				return;
			}
			
			reports.dropSummaryRequest(phone);
			comments = reports.getCommentTextsOrdered(phone);
			
			session.commit();
		}
			
		List<ChatCompletionChoice> answers = createSummary(phone, comments);
		if (answers.isEmpty()) {
			LOG.warn("No summary received for: " + phone);
		} else {
			String summary = answers.get(0).getMessage().getContent();
			
			storeSummary(db, phone, summary);
		}
				
		reschedule();
	}

	private List<ChatCompletionChoice> createSummary(String phone, List<String> comments) {
		StringBuilder question = createQuestion(phone);
		for (String comment : comments) {
			comment = comment.trim() + "\n";
			
			if (question.length() + comment.length() <= MAX_QUESTION_LENGTH) {
				question.append(comment);
			}
		}
		
		ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
				.model("gpt-3.5-turbo")
				.messages(Arrays.asList(new ChatMessage(ChatMessageRole.USER.value(), question.toString())))
		        .build();
			
		List<ChatCompletionChoice> answers = _openai.createChatCompletion(completionRequest).getChoices();
		return answers;
	}

	private static StringBuilder createQuestion(String phone) {
		StringBuilder question = new StringBuilder();
		
		question.append("Erstelle eine Zusammenfassung von Kommentaren zur Telefonnummer " + phone + ". Die Zusammenfassung soll höchstens 40 Wörter enthalten, auf Deutsch sein und sagen wer anruft. Die Kommentare lauten:\n");
		return question;
	}

	private void storeSummary(DB db, String phone, String summary) {
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			Long created = System.currentTimeMillis();
			int cnt = reports.updateSummary(phone, summary, created);
			if (cnt == 0) {
				reports.insertSummary(phone, summary, created);
			}
			
			session.commit();
	
			LOG.info("Created summary for: " + phone);
			
			_indexer.publishUpdate(phone);
		}
	}

	private void reschedule() {
		// Reset exponential back-off.
		_delaySeconds = INITIAL_DELAY_SECONDS;
		
		_process = _scheduler.executor().schedule(this::process, _delaySeconds, TimeUnit.SECONDS);
	}

	/** 
	 * Reschedules with an exponential back-off strategy.
	 */
	private void exponentialBackoff() {
		_delaySeconds = _delaySeconds * 3 / 2;
		if (_delaySeconds > TEN_MINUTE_SECONDS) {
			_delaySeconds = TEN_MINUTE_SECONDS;
		}
		
		LOG.info("Rescheduling with " + _delaySeconds + " seconds delay.");
		_process = _scheduler.executor().schedule(this::process, _delaySeconds, TimeUnit.SECONDS);
	}

}
