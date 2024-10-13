<!DOCTYPE html>
<%@page import="java.net.URLEncoder"%>
<%@page import="java.net.URL"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.app.oauth.PhoneBlockConfigFactory"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock Anmeldung");
%>
<head>
<link rel="canonical" href="https://phoneblock.net/phoneblock/" />
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<%
	boolean error = request.getAttribute("error") != null;
	String userActive = error ? "is-active" : "";
%>
<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Anmeldung</h1>
	</div>
	
	<div class="content">
		<p>
			Um Deine PhoneBlock-Einstellungen zu bearbeiten, musst Du Dich anmelden. Nachdem Du Dich angemeldet hast,
			kannst Du z.B. festlegen, ab welcher Konfidenz eine Telefonnummer zu Deiner Blocklist hinzugefügt wird  
			oder welche Länge Deine Blocklist höchstens haben darf.
		</p>
		<p>
			 Auch die Verwaltung Deiner <a href="<%=request.getContextPath() %>/anrufbeantworter">PhoneBlock-Anrufbeantworter</a>
			 erfordert eine Anmeldung.
		</p> 
	</div>

	<%
		String location = LoginServlet.location(request);
		String locationParam = LoginServlet.locationParam(request);
		String locationParamFirst = LoginServlet.locationParamFirst(request);
	%>
	
	<nav class="panel">
		<p class="panel-heading"><a href="<%=request.getContextPath()%>/oauth/login?force_client=<%=PhoneBlockConfigFactory.GOOGLE_CLIENT%>"><i class="fa-brands fa-google"></i> <span>Mit Google anmelden</span></a></p>
	</nav>
	
	<nav class="panel">
		<p class="panel-heading"><a href="#loginForm" data-action="collapse"><i class="fas fa-user"></i> <span>Mit PhoneBlock-Nutzernamen anmelden</span></a></p>
		<div id="loginForm" class="is-collapsible <%=userActive%>">
			<div class="panel-block">
	  		<div class="content">
				<form action="<%=request.getContextPath()%>/login" method="post">
				<%
					if (location != null) {
				%>
			    <input type="hidden" name="<%=LoginServlet.LOCATION_ATTRIBUTE%>" value="<%= JspUtil.quote(location) %>">
				<%
					}
				%>
				<div class="field">
				  <p class="control has-icons-left has-icons-right">
				    <input class="input" type="text" placeholder="Benutzername" name="userName">
				    <span class="icon is-small is-left">
				      <i class="fa-solid fa-user"></i>
				    </span>
				    <span class="icon is-small is-right">
				      <i class="fas fa-check"></i>
				    </span>
				  </p>
				  <p class="help">Verwende den Benutzernamen, den Du bei der <a href="<%=request.getContextPath()%>/signup.jsp<%=locationParamFirst%>">Registrierung mit E-Mail-Adresse</a> erhalten hast.</p>
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
					  <p class="help">Das Passwort wurde Dir nach der <a href="<%=request.getContextPath()%>/signup.jsp<%=locationParamFirst%>">Registrierung</a> angezeigt.</p>
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
			</div>
			</div>
			
			<div class="panel-block">
				<div class="content">
				<p>
				Wenn Du noch keinen PhoneBlock-Account hast, dann <a href="<%=request.getContextPath()%>/signup.jsp<%=locationParamFirst%>">registriere Dich</a>!
				</p>

				<p>
				Wenn Du das Passwort vergessen hast, kannst Du dieselbe E-Mail-Adresse einfach erneut registrieren. Aber 
				Vorsicht: Danach musst Du das <a href="<%=request.getContextPath()%>/setup.jsp">Passwort auch in Deiner Fritz!Box ändern</a>.
				</p>
				</div>
			</div>
		</div>
	</nav>	
</section>

<jsp:include page="footer.jspf"></jsp:include>

</body>
</html>