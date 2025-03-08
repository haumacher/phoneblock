<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock in Fritz!Box einrichten");
	request.setAttribute(LoginServlet.KEEP_LOCATION_AFTER_LOGIN, "true");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>


<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>