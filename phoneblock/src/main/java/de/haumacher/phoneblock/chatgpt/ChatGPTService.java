/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.chatgpt;

import java.io.IOException;
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

	private static final int MAX_QUESTION_LENGTH = 12000;

	private static final Logger LOG = LoggerFactory.getLogger(MetaSearchService.class);

	private DBService _db;
	private SchedulerService _scheduler;
	private IndexUpdateService _indexer;

	private ScheduledFuture<?> _heartBeat;

	private ScheduledFuture<?> _process;

	private OpenAiService _openai;
	
	/**
	 * Delay upon error.
	 */
	private int _delayMinutes = 1;

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
				LOG.info("No summary requests, sleeping.");
				
				exponentialBackoff();
				return;
			}
			
			reports.dropSummaryRequest(phone);
			comments = reports.getCommentTextsOrdered(phone);
			
			session.commit();
		}
			
		List<ChatCompletionChoice> answers = createSummary(comments);
		if (answers.isEmpty()) {
			LOG.warn("No summary received for: " + phone);
		} else {
			String summary = answers.get(0).getMessage().getContent();
			
			storeSummary(db, phone, summary);
		}
				
		reschedule();
	}

	private List<ChatCompletionChoice> createSummary(List<String> comments) {
		StringBuilder question = createQuestion();
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

	private static StringBuilder createQuestion() {
		StringBuilder question = new StringBuilder();
		
		question.append("Hi ChatGPT, Ich habe eine Liste von Kommentaren zu einer Telefonnummer. Könntest Du eine kurze Zusammenfassung von höchstens 40 Wörtern machen, die sagt, was das für eine Nummer ist und ob man ans Telefon gehen sollte? Die Kommentare lauten:\n");
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
	
			LOG.error("Created summary for: " + phone);
			
			_indexer.publishUpdate(phone);
		}
	}

	private void reschedule() {
		// Reset exponential back-off.
		_delayMinutes = 1;
		
		_process = _scheduler.executor().schedule(this::process, 20, TimeUnit.SECONDS);
	}

	/** 
	 * Reschedules with an exponential back-off strategy.
	 */
	private void exponentialBackoff() {
		_delayMinutes *= 2;
		if (_delayMinutes > 60) {
			_delayMinutes = 60;
		}
		
		_process = _scheduler.executor().schedule(this::process, _delayMinutes, TimeUnit.MINUTES);
	}

}
