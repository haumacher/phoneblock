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
		
		<h2>Schritt 7: Aboniere die Blocklist</h2>
		
		<p>
			Wähle deinen neu angelegten Account aus und aktiviere das "Blocklist"-Adressbuch. 
			Drücke jetzt den Synchronisierungsknopf. 
		</p>
		
		<div class="columns">
			<div class="column is-two-fifths">
	  			<img class="image appscreen" alt="Konto ist eingerichtet" src="<%=request.getContextPath() %>/assets/img/peoplesync/09-people-sync-account-finished.png"/>
	  		</div>
			<div class="column is-two-fifths">
	  			<img class="image appscreen" alt="Aboniere die Blocklist" src="<%=request.getContextPath() %>/assets/img/peoplesync/10-people-sync-subscribe.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/07-people-sync-add.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/11-spam-contacts.jsp">
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