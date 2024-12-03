<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock auf iPhone einrichten");
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="../header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>iPhone-Installation</h1>
		
		<p>
			Die Installation fügt das PhoneBlock-Adressbuch zu Deinen Kontakten hinzu. Wenn Du einen Anruf von einer 
			Spam-Nummer erhältst, wird dir das dann sofort angezeigt, z.B. "SPAM: 03016637169". Auch in den verpassten 
			Anrufen siehst Du gleich, dass sich hier ein Rückruf nicht lohnt, ohne erst die Nummer zu googlen. 
		</p>

		<p>
			Die Installation läuft in den folgenden Schritten:
		</p>
		
<%
		HttpSession session = request.getSession(false);
  		Object login = LoginFilter.getAuthenticatedUser(session);
  		Object token = RegistrationServlet.getPassword(session);
%>
		
   		<ol>
   		<li><a href="<%=request.getContextPath() %>/signup.jsp<%= LoginServlet.locationParamFirst(request) %>">Melde Dich bei PhoneBlock an.</a></li>
   		<li>Öffne "Einstellungen" > "Kontakte" > "Accounts". </li>
   		<li>Tippe auf "Account hinzufügen" - "Andere" > "CardDAV-Account hinzufügen".</li>
   		<li>Gib deine Zugangsdaten ein und tippe auf "Weiter".
   			<dl>
				<dt><b>Server</b></dt>
				<dd><code id="url">https://phoneblock.net<%=request.getContextPath() %>/contacts/</code> <a id="url_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></dd>

				<dt><b>Benutzername</b></dt>
				<dd>
				<%if (login != null) {%>					
				<code id="login"><%= login %></code> <a id="login_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a>
				<%} else {%>	
				Wurde Dir direkt nach der Anmeldung angezeigt.
				<%}%>
				</dd>

				<dt><b>Passwort</b></dt>
				<dd>
				<% if (token != null) {%>
				<code id="passwd"><%= token %></code> <a id="passwd_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a>
				<%} else {%>
				Wurde Dir direkt nach der Anmeldung angezeigt.
				<%}%>
				</dd>

				<dt><b>Beschreibung</b></dt>
				<dd>PhoneBlock</dd>		
   			</dl>
   		</li>
   		<li>Aktiviere "Kontakte" und tippe auf "Sichern".</li>
		<li>Nun siehst du Anrufe mit "SPAM: ..." in deinem Kontaktbuch und beim Klingeln. Du siehst dann gleich, ob es sich lohn ran zu gehen oder zurück zu rufen.</li>
   		</ol>

		<p>			
			Du hast die Zugangsdaten nicht mehr zur Hand? Macht nichts, einfach 
			<a href="<%=request.getContextPath() %>/signup.jsp<%= LoginServlet.locationParamFirst(request) %>">erneut registrieren</a> oder in den <a href="<%=request.getContextPath() + SettingsServlet.PATH %>">Einstellungen</a> das Passwort zurücksetzen.
		</p>
   		
   		<p>
   			Anderswo findest Du auch <a href="<%=request.getContextPath()%>/link/carddav-install">eine Anleitung mit Bildern</a>. 
   			Bei dieser Anleitung musst Du lediglich in dem Schritt, in dem Server, Benutzername und Passwort eingegeben werden, 
   			die PhoneBlock-Zugangsdaten benutzen (siehe oben).
   		</p>
   		
   		<p>
    		Wenn du PhoneBlock nur auf dem iPhone benutzt kannst du auch die Anzahl der Einträge <a href="<%= request.getContextPath()%><%=SettingsServlet.PATH%>">hier</a> höher stellen. 
			Sollte es damit Probleme geben, dann melde Dich bitte (siehe <a href="<%=request.getContextPath() %>/faq.jsp">FAQ</a>).
   		</p>
	</div>
</section>

<jsp:include page="../footer.jspf"></jsp:include>
</body>
</html>