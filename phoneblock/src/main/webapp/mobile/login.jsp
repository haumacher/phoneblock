<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
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
<jsp:include page="/head-content.jspf"></jsp:include>
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
	<h1>PhoneBlock-Account verknüpfen</h1>
	
	<p>
		Hier verbindest du Deinen PhoneBlock-Account mit Deinem Mobilgerät.
	</p>

<%
	Object login = LoginFilter.getAuthenticatedUser(request);
%>

<% if (login == null) { %>

	<p>
		Du musst Dich zuerst bei PhoneBlock anmelden bzw. hier einen PhoneBlock-Account erstellen.
		Du benötigst einen PhoneBlock-Account, damit Dein Mobiltelefon die PhoneBlock-Datenbank 
		für die Filterung von Anrufen nutzen kann.
	</p>
	</div>

	<jsp:include page="/login-forms.jspf">
		<jsp:param name="web" value="false" />
		<jsp:param name="locationAfterLogin" value="/mobile/login.jsp" />
	</jsp:include>

<% } else { %>

	<form action="<%=request.getContextPath()%><%=CreateAuthTokenServlet.CREATE_TOKEN %>" method="post">
		<div class="field is-grouped">
		  <p class="control">
		    <button class="button is-primary" type="submit">
	          <span class="icon">
			      <i class="fa-solid fa-link"></i>
			  </span>
		      <span>PhoneBlock-Account verknüpfen</span>
		    </button>
		  </p>
		</div>
	</form>	
	
<% } %>
</section>

<script type="text/javascript" src="<%= request.getContextPath() %>/bulma.js"></script>

</body>
</html>
