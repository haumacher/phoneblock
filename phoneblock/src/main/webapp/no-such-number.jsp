<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SearchServlet"%>
<%@page import="de.haumacher.phoneblock.app.api.model.PhoneInfo"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@page import="de.haumacher.phoneblock.app.UIProperties"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.app.api.CommentVoteServlet"%>
<%@page import="de.haumacher.phoneblock.app.ExternalLinkServlet"%>
<%@page import="de.haumacher.phoneblock.app.api.model.UserComment"%>
<%@page import="java.util.GregorianCalendar"%>
<%@page import="java.util.Calendar"%>
<%@page import="java.util.ArrayList"%>
<%@page import="de.haumacher.phoneblock.app.api.model.SearchInfo"%>
<%@page import="java.util.Map"%>
<%@page import="de.haumacher.phoneblock.app.api.model.RatingInfo"%>
<%@page import="de.haumacher.phoneblock.db.Ratings"%>
<%@page import="de.haumacher.phoneblock.app.api.model.Rating"%>
<%@page import="de.haumacher.phoneblock.app.api.model.PhoneNumer"%>
<%@page import="java.util.Date"%>
<%@page import="java.text.DateFormat"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Locale"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="de.haumacher.phoneblock.db.DB"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.db.Status"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	String number = (String) request.getAttribute(SearchServlet.NUMBER_ATTR);
%>			
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
<div class="content">
	<h1>Die Rufnummer ☎ <%= JspUtil.quote(number)%> existiert nicht</h1>

	<p>
		Bei der von Dir gesuchten Nummer handelt es sich nicht um eine Telefonnummer. 
		Bitte korrigiere Deine Sucheingabe und versuche es noch einmal!
	</p>
</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>