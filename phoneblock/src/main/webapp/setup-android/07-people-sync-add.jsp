<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
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
		
		<ol>
		<li>
			Wähle die Option "Mit URL und Benutzername anmelden" und gib als "Basis-URL" die Adresse des PhoneBlock-Adressbuchs an: <code id="url">https://phoneblock.net<%=request.getContextPath() %>/contacts/</code><a id="url_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a>.
		</li>
			
		<li>
			Bei "Benutzername" trägst Du den Benutzernamen ein, den Du bei der PhoneBlock-Anmeldung erhalten hast<%if (login != null) {%> (<code id="login"><%= login %></code><a id="login_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a>)<%}%>.
		</li>
			
		<li>
			Das Passwort wurde dir nach der ersten Anmeldung angezeigt<% if (token != null) {%> (<code id="passwd"><%= token %></code><a id="passwd_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a>)<%}%>.
		</li>
		</ol>
		
		<p>			
			Du hast die Daten nicht mehr zur Hand? Macht nichts, einfach 
			<a href="<%=request.getContextPath() %>/login.jsp<%= LoginServlet.locationParamFirst(SettingsServlet.PATH) %>">anmelden und in den Einstellungen das Passwort zurücksetzen</a>.
		</p>

		<div class="columns">
			<div class="column is-two-fifths">
	  			<img class="image appscreen" alt="Adressbuch hinzufügen" src="<%=request.getContextPath() %>/assets/img/peoplesync/07-people-sync-add.png"/>
	  		</div>
			<div class="column is-two-fifths">
	  			<img class="image appscreen" alt="Adressbuch hinzufügen" src="<%=request.getContextPath() %>/assets/img/peoplesync/08-people-sync-account.png"/>
	  		</div>
		</div>
		
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