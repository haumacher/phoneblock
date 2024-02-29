<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="../header.jspf"></jsp:include>

<%
		HttpSession session = request.getSession(false);
  		Object login = LoginFilter.getAuthenticatedUser(session);
  		Object token = RegistrationServlet.getPassword(session);
%>

<section class="section">
	<div class="content">
		<h1>Android-Installation</h1>
		
		<h2>Schritt 4: Füge ein Adressbuch hinzu und gib die PhoneBlock-Account-Daten an</h2>
		
		<div class="columns">
			<div class="column is-two-fifths">
	  			<img class="image" alt="Adressbuch hinzufügen" src="<%=request.getContextPath() %>/setup-android/07-people-sync-add.png"/>
	  		</div>
			<div class="column is-two-fifths">
	  			<img class="image" alt="Adressbuch hinzufügen" src="<%=request.getContextPath() %>/setup-android/08-people-sync-account.png"/>
	  		</div>
		</div>
		
		<p>
			Gib hier als "Basis-URL" die Adresse des PhoneBlock-Adressbuchs an: <code id="url">https://phoneblock.net<%=request.getContextPath() %>/contacts/</code> <a title="In die Zwischenablage kopieren." href="#" onclick="return copyToClipboard('url');"><i class="fa-solid fa-copy"></i></a>. Bei 
			"Benutzername" trägst Du den Benutzernamen ein, den Du bei der <a href="<%=request.getContextPath() %>/signup.jsp">PhoneBlock</a> erhalten hast<%if (login != null) {%> (<code id="login"><%= login %></code> <a title="In die Zwischenablage kopieren." href="#" onclick="return copyToClipboard('login');"><i class="fa-solid fa-copy"></i></a>)<%}%>. Das Passwort wurde dir nach erfolgreicher Registrierung angezeigt<% if (token != null) {%> (<code id="passwd"><%= token %></code> <a title="In die Zwischenablage kopieren." href="#" onclick="return copyToClipboard('passwd');"><i class="fa-solid fa-copy"></i></a>)<%}%>. Du hast die Daten nicht mehr zur Hand? Macht nichts, einfach 
			<a href="<%=request.getContextPath() %>/signup.jsp">erneut registrieren</a> oder in den <a href="<%=request.getContextPath() + SettingsServlet.PATH %>">Einstellungen</a> das Passwort zurücksetzen.
		</p>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/05-people-sync-accept.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/09-people-sync-account-finished.jsp">
		    <span>Weiter</span>
		    <span class="icon">
		      <i class="fa-solid fa-caret-right"></i>
		    </span>
		  </a>
		</p>
	</div>
</section>

<jsp:include page="../footer.jspf"></jsp:include>
</body>
</html>