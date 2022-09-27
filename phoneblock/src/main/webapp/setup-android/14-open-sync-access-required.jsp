<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
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
		
		<h2>Schritt 8: Erlaube Open Sync die Zugriff auf deine Kontakte</h2>
		
		<p>
			Damit die Synchronisation funktioniert, musst Du Open Sync den Zugriff auf deine Kontakte erlauben. Der 
			Zugriff auf den Kalender wird für PhoneBlock nicht benötigt.
		</p>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Konto ist eingerichtet" src="14-open-sync-access-required.png"/>
	  		</div>
			<div class="column">
	  			<img class="image" alt="Aboniere die Blocklist" src="15-open-sync-contact-access.png"/>
	  		</div>
			<div class="column">
	  			<img class="image" alt="Aboniere die Blocklist" src="16-open-sync-accnowledge.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="12-open-sync-account-finished.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="17-spam-contacts.jsp">
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