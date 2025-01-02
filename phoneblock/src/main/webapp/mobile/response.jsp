<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "Anmeldung erfolgreich");
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
<script type="text/javascript" src="<%= request.getContextPath() %>/mobile/response.js"></script>
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

<h1>Du bist erfolgreich angemeldet</h1>

<p>
	Dein Authorisierungstoken: <code id="token"><%=JspUtil.quote(request.getAttribute("token"))%></code>
</p>

<p>
	Deine Anmeldung an PhoneBlock war erfolgreich. Diese Seite sollte sich automatisch schließen und Dein 
	Mobilgerät sollte die Verbindung mit PhoneBlock herstellen. Wenn Dies nicht passiert, dann ist 
	möglicherweise etwas mit Deiner mobilen Anwendung nicht in Ordnung.
</p>

</div>
</section>

</body>
</html>