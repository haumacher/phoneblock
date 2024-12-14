<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.CreateAuthTokenServlet"%>
<%@page import="de.haumacher.phoneblock.app.EMailVerificationServlet"%>
<%@page import="de.haumacher.phoneblock.app.oauth.PhoneBlockConfigFactory"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<html>
<%
	request.setAttribute("title", "Auf Mobilgerät anmelden");
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<section class="hero is-small is-primary">
	<div class="hero-body">
		<p class="title">
			<img alt="PhoneBlock Logo" src="<%=request.getContextPath()%>/app-logo.svg" class="logo-image">
			PhoneBlock
		</p>
		<p class="subtitle">Der Spam-Filter fürs Telefon</p>
	</div>
  <div class="hero-foot">
</div>  
</section>

<section class="section">
	<div class="content">
	<p>
		Hier meldest Du Dich bei PhoneBlock an, um auf Deinem Mobiltelefon die PhoneBlock-Datenbank für die Filterung von 
		Anrufen nutzen und andere einfach vor neuen SPAM-Nummern warnen zu können. Wenn Du schon einen 
		PhoneBlock-Zugang hast, kannst Du diesen verwenden oder ansonsten einen neuen erstellen. 
	</p>

<%-- Login or register with Google --%>		
	<nav class="panel">
		<p class="panel-heading"><a href="<%=request.getContextPath()%>/oauth/login?force_client=<%=PhoneBlockConfigFactory.GOOGLE_CLIENT%><%=LoginServlet.locationParam(CreateAuthTokenServlet.CREATE_TOKEN)%>">
			<i class="fa-brands fa-google"></i> <span>Mit Google anmelden</span></a>
		</p>
	</nav>

<%-- Login or register with e-mail and code --%>		
<%
	Object emailMessage = request.getAttribute("emailMessage");
	String emailClass = emailMessage != null ? "is-active" : "";
%>
	<nav class="panel">
		<p class="panel-heading"><a href="#registerForm" data-action="collapse"><i class="fas fa-envelope"></i> <span>Mit E-Mail registrieren</span></a></p>
		<div id="registerForm" class="is-collapsible <%=emailClass%>">
			<form action="<%= request.getContextPath() %><%=EMailVerificationServlet.VERIFY_MOBILE%>" method="post" enctype="application/x-www-form-urlencoded">
	  		<div class="panel-block">
	  		<div class="content">
				<div class="field">
				  <label class="label">E-Mail</label>
				  <div class="control has-icons-left has-icons-right">
				    <input name="email" class="input<%= emailMessage != null ? " is-danger" : "" %>" type="email" placeholder="Deine E-Mail-Adresse" value="<%= JspUtil.quote(request.getAttribute("email")) %>">
				    <span class="icon is-small is-left">
				      <i class="fas fa-envelope"></i>
				    </span>
<% if (emailMessage != null) { %>
				    <span class="icon is-small is-right">
				      <i class="fas fa-exclamation-triangle"></i>
				    </span>
<% } %>
				  </div>
<% if (emailMessage != null) { %>
				  <p class="help is-danger">
						<%= JspUtil.quote(emailMessage) %>
				  </p>
<% } else { %>
				  <p class="help">
					Du erhälst eine E-Mail mit einem Code, mit dem Du Dich anmelden kannst.
				  </p>
<% } %>
				</div>		
						
				<div class="buttons is-right">
			    	<button class="button is-link" type="submit">Registrieren</button>
				</div>
			</div>
			</div>
			</form>
		</div>
	</nav>
		
<%-- Login with user name --%>
<%
	Object loginMessage = request.getAttribute("loginMessage");
	String loginClass = emailMessage != null ? "is-active" : "";
%>
	<nav class="panel">
		<p class="panel-heading"><a href="#loginForm" data-action="collapse"><i class="fas fa-user"></i> <span>Mit Nutzernamen anmelden</span></a></p>
		<div id="loginForm" class="is-collapsible <%=loginClass%>">
			<div class="panel-block">
	  		<div class="content">
				<form action="<%=request.getContextPath()%>/login" method="post">
				<div class="field">
				  <p class="control has-icons-left has-icons-right">
				    <input class="input" type="text" placeholder="Benutzername" name="userName">
				    <span class="icon is-small is-left">
				      <i class="fa-solid fa-user"></i>
				    </span>
				    <span class="icon is-small is-right">
				      <i class="fas fa-check"></i>
				    </span>
				  </p>
				  <p class="help">Verwende den Benutzernamen, den Du bei Deiner Registrierung erhalten hast.</p>
				</div>
				<div class="field">
				  <p class="control has-icons-left">
				    <input class="input" type="password" placeholder="PhoneBlock-Passwort" name="password">
				    <span class="icon is-small is-left">
				      <i class="fas fa-lock"></i>
				    </span>
				  </p>
<% if (loginMessage != null) {%>
					  <p class="help is-danger">
						<%= JspUtil.quote(loginMessage) %>
					  </p>
<% } else { %>
					  <p class="help">
					  	Das Passwort wurde Dir nach Deiner Registrierung auf der PhoneBlock-Webseite angezeigt.
					  </p>
<% } %>
				</div>
				
				<div class="field is-grouped is-grouped-right">
				  <p class="control">
				    <button class="button is-primary" type="submit">Anmelden</button>
				  </p>
				</div>
				</form>
			</div>
			</div>
		</div>
	</nav>	
		
	</div>
</section>

</body>
</html>
