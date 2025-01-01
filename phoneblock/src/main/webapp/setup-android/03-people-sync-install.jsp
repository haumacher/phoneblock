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
		
		<h2>Schritt 2: Installiere und öffne "PeopleSync"</h2>

		<p>
			<a href="https://play.google.com/store/apps/details?id=com.messageconcept.peoplesyncclient" target="_blank">PeopleSync</a> steht 
			übrigens in keiner Beziehung zu PhoneBlock. Mit der App wird lediglich der 
			Zugriff auf ein Internet-Adressbuch über das CardDAV-Protocoll unter Anroid ermöglicht. 
			Wenn Du also Probleme mit dieser App hast, wende Dich bitte direkt an deren Hersteller.
		</p>

		<div class="columns">
			<div class="column is-two-fifths">
	  			<img class="image appscreen" alt="Installiere PeopleSync" src="<%=request.getContextPath() %>/setup-android/03-people-sync-install.png"/>
	  		</div>
		
			<div class="column is-two-fifths">
				<img class="image appscreen" alt="Öffne PeopleSync" src="<%=request.getContextPath() %>/setup-android/04-people-sync-open.png"/>
			</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/05-people-sync-accept.jsp">
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
