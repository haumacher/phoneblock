<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock in Android Mobiltelefon einrichten");
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="../header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Android-Installation</h1>
		
		<p>
			Die Installation fügt das PhoneBlock-Adressbuch zu Deinen Kontakten hinzu. Wenn Du einen Anruf von einer 
			Spam-Nummer erhältst, wird dir das dann sofort angezeigt, z.B. "SPAM: 03016637169". Auch in den verpassten 
			Anrufen siehst Du gleich, dass sich hier ein Rückruf nicht lohnt, ohne erst die Nummer zu googlen. 
		</p>
		
		<p>
			Für die Adressbuch-Synchronisation benötigst du die App 
			"<a href="https://play.google.com/store/apps/details?id=com.messageconcept.peoplesyncclient" target="_blank">PeopleSync</a>". 
			Die Anleitung führt Dich durch die notwendigen Schritte:
		</p>
		
		<h2>Schritt 1: Öffne den Playstore und suche nach "PeopleSync"</h2>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Öffne Playstore" src="<%=request.getContextPath() %>/setup-android/01-home.png"/>
	  		</div>
		
			<div class="column">
				<img class="image" alt="Suche nach PeopleSync" src="<%=request.getContextPath() %>/setup-android/02-playstore.png"/>
			</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="#">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/03-people-sync-install.jsp">
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