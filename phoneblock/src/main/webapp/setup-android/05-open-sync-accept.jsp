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
		
		<h2>Schritt 3: Bestätige die Open Sync Lizenz</h2>
		
		<p>
			Ach ja, Open Sync steht übrigens in keiner Beziehung zu PhoneBlock. Mit der App wird lediglich der 
			Zugriff auf ein Internet-Adressbuch unter Anroid ermöglicht.
		</p>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Installiere Open Sync" src="05-open-sync-accept.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="03-open-sync-install.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="06-open-sync-accu.jsp">
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