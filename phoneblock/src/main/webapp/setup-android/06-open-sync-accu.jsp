<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="../header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Android-Installation</h1>
		
		<h2>Schritt 4: Bestätige Open Sync Einstellungen</h2>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Accu-Verbrauch" src="<%=request.getContextPath() %>/setup-android/06-open-sync-accu.png"/>
	  		</div>
			<div class="column">
	  			<img class="image" alt="Keine Tasks" src="<%=request.getContextPath() %>/setup-android/07-open-sync-no-tasks.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/05-open-sync-accept.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/08-open-sync-add.jsp">
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