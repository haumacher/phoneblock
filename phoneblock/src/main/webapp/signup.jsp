<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock Account erstellen");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
<div class="content">

<div class="columns">
  <div class="column">
	<div class="tile is-ancestor">
      <div class="tile is-parent">
      	<article class="tile is-child notification">
	        <h1 class="title">Erstelle Deinen PhoneBlock-Account</h1>
	        <p class="subtitle">Erhalte Zugangsdaten für die Einrichtung</p>
	        <div class="content">
				<p>
					Um PhoneBlock in Deinem Internet-Router zu installieren, benötigst Du einen PhoneBlock-Account. 
					Bitte registriere Dich, um Zugangsdaten für Deine Installation zu erhalten.
				</p>
				
				<form action="<%= request.getContextPath() %>/verify-email" method="post" enctype="application/x-www-form-urlencoded">
					<div class="field">
<%
				  		Object message = request.getAttribute("message");
%>
					  
					  <label class="label">E-Mail</label>
					  <div class="control has-icons-left has-icons-right">
					    <input name="email" class="input<%= message != null ? " is-danger" : "" %>" type="email" placeholder="Deine E-Mail-Adresse" value="<%= JspUtil.quote(request.getAttribute("email")) %>">
					    <span class="icon is-small is-left">
					      <i class="fas fa-envelope"></i>
					    </span>
<%
						if (message != null) {
%>
						    <span class="icon is-small is-right">
						      <i class="fas fa-exclamation-triangle"></i>
						    </span>
<%
						}
%>
					  </div>
<%
					  	if (message != null) {
%>
							<p class="help is-danger"><%= JspUtil.quote(request.getAttribute("message")) %></p>
<%
					  	}
%>
					</div>		
							
					<div class="buttons is-right">
				    	<button class="button is-link" type="submit">Registrieren</button>
					</div>
				</form>
	        </div>
	  	</article>
	  </div>
	</div>
   </div>
</div>

</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>