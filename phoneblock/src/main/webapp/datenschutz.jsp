<!DOCTYPE html>
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

			<p>
				Bei Besuch dieser Seite wird die IP-Adresse Deines Rechners zu
				Zwecken der Fehleranalyse temporär in einem Log-File gespeichert und
				nach 14 Tagen automatisch wieder gelöscht. Ohne Anmeldung erhebt
				diese Seite von ihren Besuchern keinerlei personenbezogene Daten und
				enthält weder Werbung noch Third-Party-Tracker.</p>

			<p>
				Bei Anmeldung für einen PhoneBlock-Account wird die von Dir
				angegebene E-Mail-Adresse zum Zwecke der Nutzer-Authentifizierung
				gespeichert. Die E-Mail-Adresse wird nicht an dritte weitergegeben.
				Es werden ausschließlich E-Mails zur Authentifizierung oder in
				Ausnahmefällen zum Betrieb von PhoneBlock an die angegebene Adresse
				geschickt.</p>

			<p>
				Wenn Du den PhoneBlock-Account löschen möchtest, entferne
				einfach das Adressbuch mit der Blocklist aus Deiner Fritz!Box. Wenn
				keine Abrufe der Blocklist über einen längeren Zeitraum von Deiner
				Fritz!Box mehr erfolgen, werden Deine Daten gelöscht. Wenn Dir das 
				nicht schnell genug ist, schreib mir bitte ein E-Mail an 
				<code>Bernhard Haumacher &lt;<button onclick="return showaddr(this);">...</button>&gt;</code>.
			</p>
			
			<p>
				Nur bei der Anmeldung und der Bewertung von Telefonnummern wird 
				ein technisch notwendiges Session-Cookie gesetzt. 
			</p>

			<p>
				Sollte Deine Telefonnummer irrtümlicherweise auf die Blocklist
				gelangt sein, kannst Du selbstvertändlich um Löschung ersuchen.
				Schreib eine E-Mail an 
				<code>Bernhard Haumacher &lt;<button onclick="return showaddr(this);">...</button>&gt;</code>.
			</p>

		</div>
	</section>

	<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>