<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<html>
<%
	request.setAttribute("title", "Datenschutz - PhoneBlock");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
	<jsp:include page="header.jspf"></jsp:include>

	<section class="section">
		<div class="content">
			<h1>Datenschutzerklärung</h1>

			<h2>IP-Addresse</h2>
			<p>
				Bei Besuch dieser Seite wird die IP-Adresse Deines Rechners zu
				Zwecken der Fehleranalyse temporär in einem Log-File gespeichert und
				nach 14 Tagen automatisch wieder gelöscht. Ohne Anmeldung erhebt
				diese Seite von ihren Besuchern keinerlei personenbezogene Daten und
				enthält weder Werbung noch Third-Party-Tracker.
			</p>

			<h2>E-Mail</h2>
			<p>
				Bei Anmeldung für einen PhoneBlock-Account wird die von Dir
				angegebene E-Mail-Adresse zum Zwecke der Nutzer-Authentifizierung
				gespeichert. Die E-Mail-Adresse wird nicht an dritte weitergegeben.
				Es werden ausschließlich E-Mails zur Authentifizierung oder
				zum Betrieb von PhoneBlock an die angegebene Adresse geschickt.
			</p>

			<h2>Cookies</h2>
			<p>
				Bei einigen Interaktionen mit der PhoneBlock-Webseite, z.B. bei der 
				Anmeldung und der Bewertung von Telefonnummern wird ein technisch 
				notwendiges Session-Cookie gesetzt. 
			</p>

			<h2>Account-Löschung</h2>
			<p>
				Du kannst Deinen PhoneBlock-Account jederzeit in den 
				<a href="<%=request.getContextPath()%><%=SettingsServlet.PATH%>">Einstellungen</a> 
				wieder löschen. Mit Löschung deines Accounts werden deine bei PhoneBlock
				gespeicherten Daten gelöscht. 
			</p>
			
			<h2>Telefonnummern</h2>
			<p>
				Sollte Deine Telefonnummer irrtümlicherweise auf die Blocklist
				gelangt sein, kannst Du selbstvertändlich um Löschung ersuchen.
				Schreib eine E-Mail an 
				<code>Bernhard Haumacher &lt;<button onclick="return showaddr(this);">...</button>&gt;</code>.
			</p>

			<h2>Anrufbeantworters</h2>
			<p>
				Bei der Verwendung des <a href="<%=request.getContextPath()%>/anrufbeantworter/">PhoneBlock-Anrufbeantworters</a>
				muss PhoneBlock dauerhaft die IP-Adresse Deiner Fritz!Box speichern, um den Anrufbeantworter 
				dort anzumelden und die Registrierung aufrecht zu erhalten. Hierfür hast Du entweder 
				den Host-Namen Deiner Fritz!Box hinterlegt, oder den DynDNS-Dienst von PhoneBlock in Deiner
				Fritz!Box installiert. 
			</p>
			
			<p>
				Ankommende Anrufe an deiner Fritz!Box werden einem angemeldeten Anrufbeantworter 
				angezeigt. Dabei wird die Telefonnummer des Anrufers an den PhoneBlock-Anrufbeantworter 
				übermittelt. Diese Telefonnummer wird verwendet, um sie mit der Datenbank der 
				SPAM-Telefonnummern abzugleichen. Nur wenn die Telefonnummer in der SPAM-Datenbank 
				enthalten ist, nimmt der Anrufbeantworter den Anruf an und speichert den Anruf in der 
				Anrufliste Deines Anrufbeantworters. In allen anderen Fällen werden keine Daten zu dem
				Anruf bei PhoneBlock gepeichert.
			</p>
			
		</div>
	</section>

	<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>