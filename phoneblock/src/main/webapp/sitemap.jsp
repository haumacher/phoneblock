<?xml version="1.0" encoding="UTF-8"?>
<%@page pageEncoding="UTF-8" contentType="text/xml; charset=UTF-8"
%><%@page import="java.util.Date"
%><%@page import="java.text.DateFormat"
%><%@page import="java.text.SimpleDateFormat"
%><%@page import="de.haumacher.phoneblock.db.SpamReport"
%><%@page import="java.util.List"
%><%@page import="de.haumacher.phoneblock.db.DBService"
%>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/</loc>
      <lastmod>2022-08-28</lastmod>
      <changefreq>monthly</changefreq>
      <priority>1.0</priority>
   </url>

   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/status.jsp</loc>
      <lastmod>2022-08-28</lastmod>
      <changefreq>hourly</changefreq>
      <priority>1.0</priority>
   </url>

   <url>
      <loc>https://phoneblock.haumacher.de/phoneblock/setup.jsp</loc>
      <lastmod>2022-08-28</lastmod>
      <changefreq>monthly</changefreq>
      <priority>0.5</priority>
   </url>

<%
DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
	List<SpamReport> reports = DBService.getInstance().getAll();
	long oneWeekBefore = System.currentTimeMillis() - 1000L*60*60*24*7;
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