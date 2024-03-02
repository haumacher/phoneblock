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
		
		<h2>Schritt 3: Stelle PeopleSync Berechtigungen ein</h2>
		
		<p>
			Damit die Synchronisierung und das automatische Update der Blockliste vernünfigt funktioniert, benötigt PeopleSync
			eine Reihe von Berechtigungen.  
		</p>
		
		<div class="columns">
			<div class="column is-two-fifths">
	  			<img class="image" alt="Berechtigungen" src="<%=request.getContextPath() %>/setup-android/05-people-sync-accept.png"/>
	  		</div>
			<div class="column is-two-fifths">
	  			<img class="image" alt="Sync-Intervalle" src="<%=request.getContextPath() %>/setup-android/06-people-sync-sync.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/03-people-sync-install.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/07-people-sync-add.jsp">
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