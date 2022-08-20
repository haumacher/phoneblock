<!DOCTYPE html>
<%@page import="java.util.Locale"%>
<%@page import="de.haumacher.phoneblock.db.Status"%>
<%@page import="java.util.Date"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="java.text.DateFormat"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="java.util.List"%>
<%@page import="de.haumacher.phoneblock.db.SpamReport"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<html>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<%
	SpamReport info = (SpamReport) request.getAttribute("info");
%>

<section class="section">
<div class="content">
	<h2>Telefonnummer <%= info.getPhone()%></h2>
<%
	if (info.getVotes() == 0) {
%>
	<p>
		<span class="tag is-info is-success">Keine Beschwerden</span>
	</p>

	<p>
		Die Telefonnummer ist nicht in der Datenbank vorhanden. 
		Es gibt bisher keine Beschwerden über unerwünschte Anrufe von der Telefonnummer 
		<code><%= info.getPhone() %></code>.
	</p>

	<p>
		Wenn Du Dich von Anrufen von dieser Rufnummer belästigt fühlst, trage 
		die Nummer in Deiner Fritz!Box in die Blocklist ein und schütze damit Dich und andere PhoneBlock-Nutzer
		vor weiterem Telefonterror von dieser Nummer.
	</p>
<%
	} else {
%>		
<% 
		if (info.getVotes() < 3) {
%>
	<p><span class="tag is-info is-warning">Beschwerde liegt vor</span></p>

	<p>
		Es gibt bereits Beschwerden über unerwünschte Anrufe von der Telefonnummer <code><%= info.getPhone() %></code>. 
		Die Nummer wird aber noch nicht blockiert. 
	</p>

	<p>
		Wenn Du PhoneBlock installiert hast und ebenfalls von dieser Nummer unerwünscht angerufen wurdest, trage 
		diese Nummer in Deiner Fritz!Box in die Blocklist ein und schütze damit Dich und andere PhoneBlock-Nutzer
		vor weiterem Telefonterror von dieser Nummer.
	</p>
<%
		} else {
%>			
	<p><span class="tag is-info is-danger">Blockiert</span></p>

	<p>
		Die Telefonnummer <code><%= info.getPhone() %></code> is eine mehrfach berichtete Quelle von unerwünschten 
		Telefonanrufen. Mit PhoneBlock hast Du vor Anrufen von dieser Rufnummer Ruhe.
	</p>

	<p>
		Wenn Du PhoneBlock bereits installiert hast, und trotzdem von dieser 
		Nummer angerufen wurdest, ist der Eintrag entweder ganz neu und Deine Fritz!Box hat das Update 
		noch nicht heruntergeladen, oder etwas stimmt mit Deinen Einstellungen nicht.
	</p>
<%
		}
%>			
	
	<h2>Details</h2>
	<ul>
		<li>Anzahl Beschwerden: <%= (info.getVotes() + 1) / 2 %></li>
		<li>Letzte Beschwerde vom: <%= DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.GERMAN).format(new Date(info.getLastUpdate())) %></li>
	</ul>
<%		
	}
%>
</div>

<%
	if (info.getVotes() < 3) {
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

<%
	}
%>

</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>