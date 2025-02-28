<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "Dein PhoneBlock Einrichtungscode");
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<section class="hero is-small is-primary">
	<div class="hero-body">
		<p class="title">
			<img alt="PhoneBlock Logo" src="<%=request.getContextPath()%>/assets/img/app-logo.svg" class="logo-image">
			PhoneBlock
		</p>
		<p class="subtitle">Der Spam-Filter fürs Telefon</p>
	</div>
  <div class="hero-foot">
</div>  
</section>

<section class="section">
<div class="content">

<div class="columns">
  <div class="column">
	<div class="tile is-ancestor">
      <div class="tile is-parent">
      	<article class="tile is-child notification">
	        <h1 class="title">E-Mail-Adresse bestätigen</h1>
	        <p class="subtitle">Code aus E-Mail eingeben.</p>
	        <div class="content">
				<p>
					Eine E-Mail wurde an die Adresse <code><%= JspUtil.quote(request.getAttribute("email")) %></code> gesendet. 
				</p>
				<p>
					Bitte schau in Deiner Mailbox nach, kopiere den Code und gib ihn hier ein. Damit bestätigst 
					Du Deine E-Mail-Adresse.
				</p>
				<form action="<%= request.getContextPath() %><%=RegistrationServlet.REGISTER_MOBILE%>" method="post" enctype="application/x-www-form-urlencoded">
					<div class="field">
					  <label class="label">Code</label>
					  <div class="control has-icons-left has-icons-right">
<% 
Object message = request.getAttribute("message"); 
String inputClass = message != null ? " is-danger" : "";
%>
					    <input name="code" class="input<%= inputClass %>" type="text" placeholder="Bestätigungscode" value="">
					    <span class="icon is-small is-left">
					      <i class="fa-solid fa-barcode"></i>
					    </span>
<% if (message != null) { %>
						    <span class="icon is-small is-right">
						      <i class="fas fa-exclamation-triangle"></i>
							</span>
<% } %>
					  </div>
<% if (message != null) { %>
							<p class="help is-danger">
								<%= JspUtil.quote(request.getAttribute("message")) %>
								<a href="<%=request.getContextPath()%>/mobile/login.jsp">Nochmal probieren</a>.								
							</p>
<% } else { %>
							<p class="help">
								Keine E-Mail erhalten? Prüfe bitte Deinen Spam-Ordner!
								<a href="<%=request.getContextPath()%>/mobile/login.jsp">Nochmal probieren</a>.								
							</p>
<% } %>
					</div>		
							
					<div class="buttons is-right">
				    	<button class="button is-link" type="submit">Account erstellen</button>
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

</body>
</html>