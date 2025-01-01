<?xml version="1.0" encoding="UTF-8"?>
<%@page import="de.haumacher.phoneblock.app.api.model.NumberInfo"%>
<%@page pageEncoding="UTF-8" contentType="application/xml; charset=UTF-8" session="false"
%><%@page import="java.util.Calendar"
%><%@page import="java.util.GregorianCalendar"
%><%@page import="java.text.DateFormat"
%><%@page import="java.text.SimpleDateFormat"
%><%@page import="java.util.Date"
%><%@page import="java.util.List"
%><%@page import="de.haumacher.phoneblock.db.DB"
%><%@page import="de.haumacher.phoneblock.db.DBService"
%>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
   <url>
      <loc>https://phoneblock.net/phoneblock/</loc>
      <lastmod>${maven.build.timestamp}</lastmod>
      <changefreq>monthly</changefreq>
      <priority>1.0</priority>
   </url>
<%
	DB db = DBService.getInstance();
	DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
	Long lastUpdate = db.getLastSpamReport();
%>
   <url>
      <loc>https://phoneblock.net/phoneblock/status.jsp</loc>
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
      <loc>https://phoneblock.net/phoneblock/setup.jsp</loc>
      <lastmod>${maven.build.timestamp}</lastmod>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.net/phoneblock/setup-android/</loc>
      <lastmod>${maven.build.timestamp}</lastmod>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.net/phoneblock/faq.jsp</loc>
      <lastmod>${maven.build.timestamp}</lastmod>
      <changefreq>weekly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.net/phoneblock/datenschutz.jsp</loc>
      <lastmod>${maven.build.timestamp}</lastmod>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.net/phoneblock/block.jsp</loc>
      <lastmod>${maven.build.timestamp}</lastmod>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

   <url>
      <loc>https://phoneblock.net/phoneblock/login.jsp</loc>
      <lastmod>${maven.build.timestamp}</lastmod>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

<%
	String count = request.getParameter("count");
	int pages = count == null ? 2000 : Integer.parseInt(count);
	long now = System.currentTimeMillis();
	List<? extends NumberInfo> reports = db.getAll(pages);
	long oneWeekBefore = now - 1000L*60*60*24*7;
	for (NumberInfo report : reports) {
%>
   <url>
      <loc>https://phoneblock.net/phoneblock/nums/<%=report.getPhone() %></loc>
      <lastmod><%= format.format(new Date(report.getUpdated())) %></lastmod>
      <changefreq><%= report.getUpdated() < oneWeekBefore ? "weekly" : "daily" %></changefreq>
      <priority>0.3</priority>
   </url>
<%
	}
%>

</urlset>