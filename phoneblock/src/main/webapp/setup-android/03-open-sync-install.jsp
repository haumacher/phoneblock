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
		
		<h2>Schritt 2: Installiere und öffne "Open Sync"</h2>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Installiere Open Sync" src="03-open-sync-install.png"/>
	  		</div>
		
			<div class="column">
				<img class="image" alt="Öffne Open Sync" src="04-open-sync-open.png"/>
			</div>
		</div>
		
		<p class="buttons is-centered">
		  <a class="button" href="./">
		    <span class="icon">
		      <i class="fa-solid fa-caret-left"></i>
		    </span>
		    <span>Zurück</span>
		  </a>
		  
		  <a class="button is-primary" href="05-open-sync-accept.jsp">
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