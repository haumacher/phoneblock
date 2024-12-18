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
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
<div class="content">

<h1>E-Mail bestätigen</h1>

<p>
	Eine E-Mail wurde an die Adresse <code><%= JspUtil.quote(request.getAttribute("email")) %></code> gesendet. 
	Bitte schau in Deiner Mailbox nach und kopiere den Code.
</p>

<div class="columns">
  <div class="column">
	<div class="tile is-ancestor">
      <div class="tile is-parent">
      	<article class="tile is-child notification">
	        <h1 class="title">Code eingeben</h1>
	        <p class="subtitle">E-Mail-Adresse bestätigen.</p>
	        <div class="content">
				<p>
					Bitte bestätige Deine E-Mail-Adresse, indem Du den Code aus der Anmelde-E-Mail hier eingibst.
				</p>
<%
				String location = LoginServlet.location(request);
%>
				<form action="<%= request.getContextPath() %><%=RegistrationServlet.REGISTER_WEB%>" method="post" enctype="application/x-www-form-urlencoded">
					<div class="field">
<%
				  		Object message = request.getAttribute("message");
%>
					  <label class="label">Code</label>
					  <div class="control has-icons-left has-icons-right">
						<%
							if (location != null) {
						%>
					    <input type="hidden" name="<%=LoginServlet.LOCATION_ATTRIBUTE%>" value="<%= JspUtil.quote(location) %>">
						<%
							}
						%>

					    <input name="code" class="input<%= message != null ? " is-danger" : "" %>" type="text" placeholder="Bestätigungscode" value="">
					    <span class="icon is-small is-left">
					      <i class="fa-solid fa-barcode"></i>
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
							<p class="help is-danger">
								<%= JspUtil.quote(request.getAttribute("message")) %>
								<a href="<%=request.getContextPath()%>/signup.jsp<%= LoginServlet.locationParamFirst(request) %>">Nochmal probieren</a>.								
							</p>
<%
						} else {
%>
							<p class="help is-info">
								Keine E-Mail erhalten? Prüfe bitte Deinen Spam-Ordner!
								<a href="<%=request.getContextPath()%>/signup.jsp<%= LoginServlet.locationParamFirst(request) %>">Nochmal probieren</a>.								
							</p>
<%							
					  	}
%>
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

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>