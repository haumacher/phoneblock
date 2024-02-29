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
		
		<h2>Fertig, die Blocklist ist in Deinen Kontakten eingetragen</h2>
		
		<p>
			Öffne Deine Kontakte, unter "S" hast Du eine lange Liste von Spam-Nummern, bei denen Du besser nicht 
			rangehst und nicht zurückrufst.
		</p>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Konto ist eingerichtet" src="<%=request.getContextPath() %>/setup-android/11-spam-contacts.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/09-people-sync-account-finished.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/status.jsp">
		    <span>Fertig</span>
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