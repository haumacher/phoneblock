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

<div class="columns">
  <div class="column">
	<div class="tile is-ancestor">
      <div class="tile is-parent">
      	<article class="tile is-child notification">
	        <p class="title">Registriere Deinen PhoneBlock-Account</p>
	        <p class="subtitle">Erhalte Zugangsdaten für die Einrichtung</p>
	        <div class="content">
				<p>
					Um PhoneBlock in Deinem Internet-Router zu installieren, benötigst Du einen PhoneBlock-Account. 
					Bitte melde Dich hier an, um Zugangsdaten für Deine Installation zu erhalten.
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
				    	<button class="button is-link" type="submit">Anmelden</button>
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