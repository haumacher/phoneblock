/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.users;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import de.haumacher.phoneblock.db.DB;

/**
 * Tool entering a new user to the PhoneBlock DB.
 */
public class UserAdd {
	
	private static final String SAVE_CHARS = "123456789qwertzuiopasdfghjkyxcvbnmQWERTZUPASDFGHJKLYXCVBNM";

	public static void main(String[] args) throws SQLException, IOException {
		DB db = new DB(createDataSource());
		
		String userName;
		if (args.length == 0) {
			System.out.print("E-Mail: ");
			userName = new BufferedReader(new InputStreamReader(System.in)).readLine();
		} else {
			userName = args[0];
		}
		SecureRandom rnd = new SecureRandom();
		StringBuilder pwbuffer = new StringBuilder();
		for (int n = 0; n < 20; n++) {
			pwbuffer.append(SAVE_CHARS.charAt(rnd.nextInt(SAVE_CHARS.length())));
		}
		
		String passwd = pwbuffer.toString();
		db.addUser(userName, passwd);
		
		System.out.println(passwd);
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
