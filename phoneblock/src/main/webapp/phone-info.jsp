<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8"%>
<%@page import="de.haumacher.phoneblock.analysis.PhoneNumer"%>
<%@page import="de.haumacher.phoneblock.analysis.NumberAnalyzer"%>
<%@page import="java.util.Date"%>
<%@page import="java.text.DateFormat"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Locale"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="de.haumacher.phoneblock.db.DB"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.db.SpamReport"%>
<%@page import="de.haumacher.phoneblock.db.Status"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<%
	String userAgent = request.getHeader("User-Agent");
	boolean android = userAgent != null && userAgent.toLowerCase().contains("android");
	PhoneNumer analysis = (PhoneNumer) request.getAttribute("number");
	SpamReport info = (SpamReport) request.getAttribute("info");
	int complaints = (info.getVotes() + 1) / 2;
%>

<section class="section">
<div class="content">
	<h1>Telefonnummer ☎ <%= info.getPhone()%></h1>
	
<%
	if (info.getVotes() == 0) {
%>
	<p>
		<span class="tag is-info is-success">Keine Beschwerden</span>
	</p>

	<p>
		Die Telefonnummer ist nicht in der <a href="<%=request.getContextPath() %>/">PhoneBlock</a>-Datenbank vorhanden. 
		Es gibt bisher keine Beschwerden über unerwünschte Anrufe von der Telefonnummer 
		☎ <code><%= info.getPhone() %></code>.
	</p>

<% if (!android) { %>
	<p>
		Wenn Du Dich von Anrufen von dieser Rufnummer belästigt fühlst, trage 
		die Nummer in Deiner Fritz!Box in die Blocklist ein und schütze damit Dich und andere PhoneBlock-Nutzer
		vor weiterem Telefonterror von dieser Nummer.
	</p>
<% } %>

<%
	} else {
%>		
<% 
		if (info.getVotes() < DB.MIN_VOTES) {
%>
	<p><span class="tag is-info is-warning">Beschwerde liegt vor</span></p>

	<p>
		Es gibt bereits <% if (complaints == 1) { %>eine Beschwerde<%} else {%><%= complaints %> Beschwerden<%}%> über unerwünschte Anrufe von der 
		Telefonnummer ☎ <code><%= info.getPhone() %></code>. Die Nummer wird aber noch nicht blockiert. 
	</p>

<% if (!android) { %>
	<p>
		Wenn Du <a href="<%=request.getContextPath() %>/">PhoneBlock</a> installiert hast und ebenfalls von dieser Nummer unerwünscht angerufen wurdest, trage 
		diese Nummer in Deiner Fritz!Box in die Blocklist ein und schütze damit Dich und andere PhoneBlock-Nutzer
		vor weiterem Telefonterror von dieser Nummer.
	</p>
<% } %>

<%
		} else {
%>			
	<p><span class="tag is-info is-danger">Blockiert</span></p>

	<p>
		Die Telefonnummer ☎ <code><%= info.getPhone() %></code> is eine mehrfach berichtete Quelle von unerwünschten 
		Telefonanrufen. Mit <a href="<%=request.getContextPath() %>/">PhoneBlock</a> hast Du vor Anrufen von dieser und 
		<a href="<%=request.getContextPath() %>/status.jsp">vieler anderer Rufnummern</a> sofort Ruhe.
	</p>

<% if (!android) { %>
	<p>
		Wenn Du <a href="<%=request.getContextPath() %>/">PhoneBlock</a> bereits installiert hast, und trotzdem von dieser 
		Nummer angerufen wurdest, ist der Eintrag entweder ganz neu und Deine Fritz!Box hat das Update 
		noch nicht heruntergeladen, oder etwas stimmt mit Deinen Einstellungen nicht.
	</p>
<% } %>
<%
		}
	}
%>

	<h2>Details</h2>
<%
	DateFormat format = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.GERMAN);
%>			
	<ul>
		<li>Alternative Schreibweisen: <%if (analysis.getShortcut() != null) {%><code><%= analysis.getShortcut() %></code>, <%}%><code><%= analysis.getPlus() %></code>, <code><%= analysis.getZeroZero() %></code></li>
		<li>Land: <%= analysis.getCountry() %> (<code><%= analysis.getCountryCode() %></code>)</li>

		<%if (analysis.getCity() != null) { %>		
		<li>Stadt: <%= analysis.getCity() %> (<code><%= analysis.getCityCode() %></code>)</li>
		<%}%>

<%
		if (complaints > 0) {
%>	
		<li>Anzahl Beschwerden: <%= complaints %></li>
		<li>Letzte Beschwerde vom: <%= format.format(new Date(info.getLastUpdate())) %></li>

<%
			long dateAdded = info.getDateAdded();
			if (dateAdded > 0) {
%>
		<li>Nummer aktiv seit: <%= format.format(new Date(dateAdded)) %></li>
<%			
			}
		} 
%>
	</ul>
	
<% if (android) { %>

	<h2>Keine Lust mehr nach Telefonnummern zu googeln?</h2>
	<p>
		<a href="<%=request.getContextPath() %>/setup-android/">Installiere PhoneBlock auf Deinem Android-Mobiltelefon</a> 
		und du weißt sofort, ob es sich lohnt den Anruf anzunehmen oder eine Nummer zurückzurufen. 
	</p>	
<% } %>

</div>

<% if (android) { %>
<div class="tile is-ancestor">
	<div class="tile is-parent is-6">
		<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/setup-android/">
			<p class="title">PhoneBlock für Android</p>
			<p class="subtitle">Noch nicht installiert? Dann los!</p>
		</a>
	</div>
</div>

<% } else { %>

<%
	if (info.getVotes() < DB.MIN_VOTES) {
%>

<div class="tile is-ancestor">
	<div class="tile is-parent is-6 ">
		<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/block.jsp">
			<p class="title">Rufnummer sperren</p>
			<p class="subtitle">Melde neue Quelle von Telefonterror!</p>
		</a>
	</div>

	<div class="tile is-parent is-6">
		<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/signup.jsp">
			<p class="title">PhoneBlock installieren</p>
			<p class="subtitle">Noch nicht installiert? Dann los!</p>
		</a>
	</div>
</div>

<%
	} else {
%>

<div class="tile is-ancestor">
	<div class="tile is-parent is-6">
		<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/signup.jsp">
			<p class="title">PhoneBlock installieren</p>
			<p class="subtitle">Account erstellen und einrichten!</p>
		</a>
	</div>

	<div class="tile is-parent is-6 ">
		<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/setup.jsp">
			<p class="title">Installation überprüfen</p>
			<p class="subtitle">Check die Installationsanleitung!</p>
		</a>
	</div>
</div>

<% } %>
<% } %>

</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>