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
			Wähle deinen neu angelegten Account aus und selektiere das "Blocklist"-Adressbuch. 
			Drücke jetzt den Synchronisierungsknopf. Die Synchronisation klappt noch 
			nicht, du musst zuerst den Zugriff auf deine Kontakte erlauben. Wische dafür von oben nach unten, um die 
			Systemmeldungen anzuzeigen. Dort erhältst Du eine Aufforderung, den Zugriff zu erlauben. 
		</p>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Konto ist eingerichtet" src="<%=request.getContextPath() %>/setup-android/12-open-sync-account-finished.png"/>
	  		</div>
			<div class="column">
	  			<img class="image" alt="Aboniere die Blocklist" src="<%=request.getContextPath() %>/setup-android/13-open-sync-subscribe.png"/>
	  		</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="<%=request.getContextPath() %>/setup-android/10-open-sync-configure.jsp">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="<%=request.getContextPath() %>/setup-android/14-open-sync-access-required.jsp">
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