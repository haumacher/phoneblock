<?xml version="1.0" encoding="UTF-8"?>
<%@page import="java.util.Calendar"%>
<%@page import="java.util.GregorianCalendar"%>
<%@page pageEncoding="UTF-8" contentType="application/xml; charset=UTF-8" session="false"
%><%@page import="java.text.DateFormat"
%><%@page import="java.text.SimpleDateFormat"
%><%@page import="java.util.Date"
%><%@page import="java.util.List"
%><%@page import="de.haumacher.phoneblock.db.DB"
%><%@page import="de.haumacher.phoneblock.db.DBService"
%><%@page import="de.haumacher.phoneblock.db.SpamReport"
%>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/</loc>
      <changefreq>monthly</changefreq>
      <priority>1.0</priority>
   </url>
<%
	DB db = DBService.getInstance();
	DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
	Long lastUpdate = db.getLastSpamReport();
%>
   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/status.jsp</loc>
<%
	if (lastUpdate != null) {
%>
      <lastmod><%= format.format(new Date(lastUpdate.longValue())) %></lastmod>
<%		
	}
%>
      <changefreq>hourly</changefreq>
      <priority>1.0</priority>
   </url>

   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/setup.jsp</loc>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/faq.jsp</loc>
      <changefreq>weekly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/datenschutz.jsp</loc>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/block.jsp</loc>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/signup.jsp</loc>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

<%
	long now = System.currentTimeMillis();
	long age = now - new GregorianCalendar(2022, Calendar.SEPTEMBER, 19).getTimeInMillis();

	// Simulate site growth to convince google reading all pages.
	int limit = 50 + (int) (age / (1000L*60*60*24)) * 10;
	List<SpamReport> reports = db.getAll(limit);
	long oneWeekBefore = now - 1000L*60*60*24*7;
	for (SpamReport report : reports) {
%>
   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/nums/<%=report.getPhone() %></loc>
      <lastmod><%= format.format(new Date(report.getLastUpdate())) %></lastmod>
      <changefreq><%= report.getLastUpdate() < oneWeekBefore ? "weekly" : "daily" %></changefreq>
      <priority>0.3</priority>
   </url>
<%
	}
%>

</urlset>