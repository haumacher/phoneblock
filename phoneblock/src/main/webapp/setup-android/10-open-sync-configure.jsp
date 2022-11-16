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
		
		<h2>Schritt 6: Stelle die Synchronisierungsoptionen für das Adressbuch ein</h2>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Gruppeneinstellungen" src="<%=request.getContextPath() %>/setup-android/10-open-sync-configure.png"/>
	  		</div>
			<div class="column">
	  			<img class="image" alt="Gruppen sind Kategorien" src="<%=request.getContextPath() %>/setup-android/11-open-sync-groups.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/08-open-sync-add.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/12-open-sync-account-finished.jsp">
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