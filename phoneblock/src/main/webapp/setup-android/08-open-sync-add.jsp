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
		
		<h2>Schritt 5: Füge ein Adressbuch hinzu und gib die PhoneBlock-Account-Daten an</h2>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Adressbuch hinzufügen" src="08-open-sync-add.png"/>
	  		</div>
			<div class="column">
	  			<img class="image" alt="Keine Tasks" src="09-open-sync-account.png"/>
	  		</div>
		</div>
		
		<p>
			Gib hier als "Basis-URL" die Adresse des PhoneBlock-Adressbuchs an: <code>https://phoneblock.haumacher.de<%=request.getContextPath() %>/contacts/</code>. Bei 
			"Benutzername" trägst Du die E-Mail-Adresse ein, mit der du dich <a href="../signup.jsp">bei PhoneBlock 
			registriert</a> hast. Das Passwort wurde dir nach erfolgreicher Registrierung angezeigt. Du hast die Daten nicht mehr zur Hand? Macht nichts, einfach 
			<a href="../signup.jsp">erneut registrieren</a>.
		</p>
		
		<p class="buttons is-centered">
		  <a class="button" href="06-open-sync-accu.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="10-open-sync-configure.jsp">
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