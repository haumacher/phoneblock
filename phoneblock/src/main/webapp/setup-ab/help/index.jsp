<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock-Anrufbeantworter einrichten");
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="../header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>PhoneBlock-Anrufbeantworter einrichten</h1>
		
		<p>
			Der PhoneBlock-Anrufbeantworter wird als "IP-Telefon mit Anrufbeantworter" an der Fritz!Box (oder einem 
			anderen Internet-Router) eingerichtet. Die Einrichtung läuft in den folgenden Schritte ab: 
		</p>
	
<%-- 
 		<i class="fa-solid fa-circle-check"></i>
--%>
		
		<h2><i class="fa-regular fa-circle"></i> An der Fritz!Box anmelden</h2>
		<h2><i class="fa-regular fa-circle"></i> DynDNS einrichten</h2>
		<h2><i class="fa-regular fa-circle"></i> Neues Telfoniegerät einrichten</h2>
		<h2><i class="fa-regular fa-circle"></i> Telefoniegerät als IP-Telefon mit Anrufbeantworter markieren</h2>
		<h2><i class="fa-regular fa-circle"></i> Anmeldedaten des IP-Telefons eintragen</h2>
		<h2><i class="fa-regular fa-circle"></i> Lokale Telefonnummer auswählen</h2>
		<h2><i class="fa-regular fa-circle"></i> Zu schützende Telefonnummer(n) auswählen</h2>
		<h2><i class="fa-regular fa-circle"></i> Eingaben überprüfen</h2>
		<h2><i class="fa-regular fa-circle"></i> Ausgehende Anrufe deaktivieren</h2>
		<h2><i class="fa-regular fa-circle"></i> Zugang aus dem Internet freischalten</h2>
		
		
		<p class="buttons is-centered">
		  <a class="button" href="#">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/03-open-sync-install.jsp">
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