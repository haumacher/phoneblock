<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock Anmeldung");
%>
<head>
<link rel="canonical" href="https://phoneblock.haumacher.de/phoneblock/" />
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<%
	boolean error = request.getAttribute("error") != null;
%>
<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Anmeldung</h1>
	</div>

	<form action="<%=request.getContextPath()%>/login" method="post">
	<div class="field">
	  <p class="control has-icons-left has-icons-right">
	    <input class="input" type="email" placeholder="E-Mail" name="userName">
	    <span class="icon is-small is-left">
	      <i class="fas fa-envelope"></i>
	    </span>
	    <span class="icon is-small is-right">
	      <i class="fas fa-check"></i>
	    </span>
	  </p>
	  <p class="help">Verwende die E-Mail-Adresse, mit der Du Dich bei PhoneBlock <a href="<%=request.getContextPath()%>/signup.jsp">registriert hast<a></a>.</p>
	</div>
	<div class="field">
	  <p class="control has-icons-left">
	    <input class="input" type="password" placeholder="PhoneBlock-Passwort" name="password">
	    <span class="icon is-small is-left">
	      <i class="fas fa-lock"></i>
	    </span>
	  </p>
	  <% if (error) {%>
		  <p class="help is-danger">Die Anmeldedaten stimmen nicht überein, bitte überprüfe die Eingaben und versuche es noch einmal.</p>
	  <% } else { %>
		  <p class="help">Das Passwort wurde Dir nach der <a href="<%=request.getContextPath()%>/signup.jsp">Registrierung</a> angezeigt.</p>
	  <% } %>
	</div>
	
	<div class="field is-grouped is-grouped-right">
	  <p class="control">
	    <button class="button is-primary" type="submit">
	      Anmelden
	    </button>
	  </p>
	</div>
	</form>
	
	<div class="content">
		<p>
			Noch kein PhoneBlock-Account? Dann <a href="<%=request.getContextPath()%>/signup.jsp">registriere Dich</a>! 
			Wenn Du das Passwort vergessen hast, kannst Du dieselbe E-Mail-Adresse einfach erneut registrieren. Aber 
			Vorsicht: Danach musst Du das <a href="<%=request.getContextPath()%>/setup.jsp">Passwort auch in Deiner Fritz!Box ändern</a>.
		</p>		
	</div>

</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>