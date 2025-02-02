<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock Impressum");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Impressum</h1>

		<p>
			PhoneBlock ist ein <a href="https://github.com/haumacher/phoneblock">privates Projekt</a>, 
			das Dir bei der digitalen Selbstverteidigung gegen Telefonterror und unerwünschte Werbeanrufe
			helfen kann. PhoneBlock ist weder ein Produkt noch eine kommerzielle Dienstleistung im
			Sinne des <a href="https://www.gesetze-im-internet.de/ddg/__5.html">Digitale-Dienste-Gesetzes</a>. 
		</p>
		
		<p>
			Diese Webseite betreibe ich (Bernhard Haumacher &lt;<button class="showaddr">...</button>&gt;) 
			als Privatperson zu meinem persönlichen Vergnügen und aus technologischem Interesse. 
			Ich ermögliche Dir die kostenlose Nutzung der Software und stelle Dir die dafür notwendigen 
			Server-Kapazitäten ebenfalls unentgeltlich zur Verfügung. Wenn Du Dich an den Betriebskosten beteiligen, 
			oder einfach nur "Danke" sagen möchtest, dann kannst Du gerne eine 
			<a href="<%=request.getContextPath()%>/support.jsp">kleinen Betrag "spenden"</a>. 
			Du tust dies aber ohne die Erwartung jedweder Gegenleistung.
		</p>
		
		<p>
			Die Software hinter dieser Webseite wurde von Bernhard Haumacher &lt;<button class="showaddr">...</button>&gt;
			und anderen entwickelt. Bei Fragen, die nicht schon in der <a href="<%=request.getContextPath()%>/faq.jsp">FAQ</a> 
			und den <a href="<%=request.getContextPath()%>/usage.jsp">Nutzungsbedingungen</a> 
			beantwortet werden, darfst du Dich gerne an mich (<button class="showaddr">...</button>) wenden. 
			Allerdings wünsche ich weder Telefonanrufe noch Besuche, weswegen hier E-Mail 
			als ausschließliche Kontaktmöglichkeit ausreichen muss. Bei Problemen, z.B. wenn Deine 
			Telefonnummer versehentlich auf die Blockliste geraten ist, helfe ich gerne umgehend und unkompliziert.  
		</p>

		</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>