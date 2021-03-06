<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page pageEncoding="UTF-8" %>
<html>
<head>
	<title>PhoneBlock: Der Spam-Filter für Dein Telefon</title>
	
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
	<link rel="stylesheet" href="<%= request.getContextPath() %>/webjars/font-awesome/6.1.0/css/all.min.css">
	<script type="text/javascript" src="phoneblock.js"></script>
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
	        <p class="title">Code eingeben</p>
	        <p class="subtitle">E-Mail-Adresse bestätigen.</p>
	        <div class="content">
				<p>
					Bitte bestätige Deine E-Mail-Adresse, indem Du den Code aus der Anmelde-E-Mail hier eingibst.
				</p>
				
				<form action="<%= request.getContextPath() %>/registration-code" method="post" enctype="application/x-www-form-urlencoded">
					<div class="field">
<%
				  		Object message = request.getAttribute("message");
%>
					  <label class="label">Code</label>
					  <div class="control has-icons-left has-icons-right">
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
							<p class="help is-danger"><%= JspUtil.quote(request.getAttribute("message")) %></p>
<%
						} else {
%>
							<p class="help is-info">Keine E-Mail erhalten? Prüfe bitte Deinen Spam-Ordner!</p>
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