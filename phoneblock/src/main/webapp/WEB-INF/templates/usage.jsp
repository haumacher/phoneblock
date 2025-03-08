<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock Nutzungsbedingungen");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Nutzungsbedingungen</h1>
		
		<p>
			PhoneBlock kann Dir bei der digitalen Selbstverteidigung gegen Telefonterror und unerwünschte 
			Werbeanrufe helfen. PhoneBlock ist <a href="<%=request.getContextPath()%>/impressum.jsp">mein</a> 
			privates <a href="https://github.com/haumacher/phoneblock">Projekt</a> und ich lasse Dich gerne 
			unentgeldlich daran teilhaben.
		</p>
		
		<h2>Haftungsausschluss</h2>
		
		<p>
			Die Nutzung von PhoneBlock erfolgt ausschließlich und ausdrücklich
			auf eigene Gefahr. Du bist Dir bei der Nutzung bewusst, dass durch
			einen Fehler in der Software, oder durch möglicherweise böswilliges
			Einfügen von Nummern in die Blockliste eine Nummer gesperrt werden
			könnte, von der du vielleich angerufen werden willst.
		</p>
		
		<h2>Kommerzieller Einsatz</h2>
		
		<p>
			Ich rate davon ab, PhoneBlock an einem geschäftlichen Telefonanschluss 
			einzusetzen. Wenn Du das trotzdem tun möchtest, dann trägst Du auch 
			hierbei das alleinige Risiko für alle daraus möglicherweise resultierenden 
			Schäden. Um das Einsatzrisiko zu minimieren, empfehle ich Dir, Nummern
			der Blockliste nicht zu sperren, sondern nur auf Deinen  Anrufbeantworter 
			umzuleiten. Im kommerziellen Umfeld rate ich außerdem dringend vom Einsatz des
			PhoneBlock-Anrufbeantworters ab - unter keinen
			Umständen, willst Du riskieren einen potentiellen Kunden an Deinem
			Telefonanschluss von dem Anrufbeantworter "veräppeln" zu lassen.
		</p>
		
		<h2>Betrieb des PhoneBlock-Servers</h2>
		
		<p>
			PhoneBlock steht Dir unentgeldlich zur Verfügung. Der Betrieb einer 
			Webanwendung ist aber nicht kostenlos. Wenn Dir PhoneBlock hilft, kannst Du Dich
			gerne in Form einer <a href="<%=request.getContextPath()%>/support.jsp">kleinen Spende</a>
			an den Betriebskosten beteiligen. Du tust dies aber ohne die Erwartung jedweder 
			Gegenleistung.
		</p>
		
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>