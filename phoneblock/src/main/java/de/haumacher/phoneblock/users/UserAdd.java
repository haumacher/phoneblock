/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.users;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Tool entering a new user to the PhoneBlock DB.
 */
public class UserAdd {
	
	public static void main(String[] args) throws SQLException, IOException {
		SchedulerService scheduler = new SchedulerService();
		scheduler.contextInitialized(null);
		DB db = new DB(createDataSource(), scheduler);
		
		String login;
		if (args.length == 0) {
			System.out.print("Login name: ");
			login = new BufferedReader(new InputStreamReader(System.in)).readLine();
		} else {
			login = args[0];
		}
		String passwd = db.createUser(login, login);
		
		System.out.println(passwd);
		
		scheduler.contextDestroyed(null);
	}

	private static DataSource createDataSource() throws IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(new File(".phoneblock")));
		
		JdbcDataSource result = new JdbcDataSource();
		result.setUrl(properties.getProperty("db.url"));
		result.setUser(properties.getProperty("db.user"));
		result.setPassword(properties.getProperty("db.passwd"));
		return result;
	}

}
