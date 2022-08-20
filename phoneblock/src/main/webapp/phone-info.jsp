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
	if (info.getVotes() == 0) {
%>
<section class="section">
<div class="content">
	<h2>Telefonnummer <%= info.getPhone()%></h2>

	<p>
		Status: <span class="tag is-info is-success">Keine Beschwerden</span>
	</p>

	<p>
		Die Telefonnummer ist nicht in der Datenbank vorhanden. 
		Es gibt bisher keine Beschwerden über unerwünschte Anrufe von der Telefonnummer 
		<code><%= info.getPhone() %></code>.
	</p>
</div>

<div class="columns">
  <div class="column is-half is-offset-one-quarter">
	<a href="<%=request.getContextPath() %>/block.jsp"><button class="button is-medium is-primary is-fullwidth">Rufnummer sperren</button></a>
  </div>
</div>

</section>
<%
	} else {
%>		
<section class="section">
<div class="content">
	<h2>Telefonnummer <%= info.getPhone()%></h2>

	<p>
		Status: <span class="tag is-info is-danger">Blockiert</span>
	</p>

	<p>
		Die Telefonnummer <code><%= info.getPhone() %></code> wurde bereits als Quelle von unerwünschten 
		Telefonanrufen gemeldet. Mit PhoneBlock hast Du vor Anrufen von dieser Rufnummer Ruhe.
	</p>
	
	<p>
		Wenn Du PhoneBlock bereits installiert hast, und trotzdem von dieser 
		Nummer angerufen wurdest, ist der Eintrag entweder ganz neu und Deine Fritz!Box hat das Update 
		noch nicht heruntergeladen, oder etwas stimmt mit Deinen Einstellungen nicht.
	</p>
	
	<h2>Details</h2>
	<ul>
		<li>Anzahl Beschwerden: <%= info.getVotes() %></li>
		<li>Letzte Beschwerde vom: <%= DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.GERMAN).format(new Date(info.getLastUpdate())) %></li>
	</ul>
</div>

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

</section>
<%		
	}
%>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>