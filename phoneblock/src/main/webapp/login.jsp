<%
	// If already logged in, forward to final destination.
	String userName = LoginFilter.getAuthenticatedUser(request);
	if (userName != null) {
		LoginServlet.redirectToLocationAfterLogin(request, response);
		return;
	}
%>
<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.app.EMailVerificationServlet"%>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
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
		String location = LoginServlet.location(request, SettingsServlet.PATH);
		String locationParam = LoginServlet.locationParam(location);
		String locationParamFirst = LoginServlet.locationParamFirst(location);
	%>
	
	<nav class="panel">
		<p class="panel-heading">
			<a href="#googleLogin" data-action="collapse"><i class="fa-brands fa-google"></i> <span>Mit Google anmelden</span></a>
		</p>
		<div id="googleLogin" class="is-collapsible">
			<div class="panel-block">
	  		<div class="content">
				<form action="<%=request.getContextPath()%>/oauth/login" method="get">

			    <input type="hidden" name="force_client" value="<%=PhoneBlockConfigFactory.GOOGLE_CLIENT%>">
<% if (location != null) { %>
			    <input type="hidden" name="<%=LoginServlet.LOCATION_ATTRIBUTE%>" value="<%= JspUtil.quote(location) %>">
<% } %>
				<div class="field">
				  <div class="control">
	  				<label class="checkbox">
					  <input type="checkbox" name="<%=LoginServlet.REMEMBER_PARAM%>" value="true"/>
					  <span>Auf diesem Gerät angemeldet bleiben (setzt ein <a href="<%=request.getContextPath()%>/datenschutz.jsp">Cookie</a>)</span>
					</label>
				  </div>
				</div>
				
				<div class="field is-grouped">
				  <p class="control">
				    <button class="button is-primary" type="submit">
			          <span class="icon">
					      <i class="fa-solid fa-right-to-bracket"></i>
					  </span>
				      <span>Weiter zu Google</span>
				    </button>
				  </p>
				</div>
				</form>
			</div>
			</div>
		</div>
	</nav>

<%
	Object emailMessage = request.getAttribute(EMailVerificationServlet.VERIFY_ERROR_ATTR);
	boolean emailError = emailMessage != null;
	String emailActive = emailError ? "is-active" : "";
	String inputClass = emailError ? "input is-danger" : "input";
%>
	<nav class="panel">
		<p class="panel-heading"><a href="#emailLogin" data-action="collapse"><i class="fas fa-envelope"></i> <span>Mit E-Mail anmelden</span></a></p>
		<div id="emailLogin" class="is-collapsible <%=emailActive%>">
			<form action="<%= request.getContextPath() %><%= EMailVerificationServlet.LOGIN_WEB%>" method="post" enctype="application/x-www-form-urlencoded">
	  		<div class="panel-block">
	  		<div class="content">
<% if (location != null) { %>
			    <input type="hidden" name="<%=LoginServlet.LOCATION_ATTRIBUTE%>" value="<%= JspUtil.quote(location) %>">
<% } %>
				<div class="field">
					<label class="label">E-Mail</label>
					<div class="control has-icons-left has-icons-right">
					    <input name="email" 
					    	class="<%= inputClass %>" 
					    	type="email" 
					    	placeholder="Deine E-Mail-Adresse" 
					    	value="<%= JspUtil.quote(request.getAttribute("email")) %>"
					    />
					    <span class="icon is-small is-left">
					      <i class="fas fa-envelope"></i>
					    </span>
<% if (emailError) { %>
					    <span class="icon is-small is-right">
					      <i class="fas fa-exclamation-triangle"></i>
					    </span>
<% } %>
					</div>
<% if (emailError) { %>
					<p class="help is-danger">
						<%= JspUtil.quote(request.getAttribute("message")) %>
					</p>
<% } %>
				</div>

				<div class="field">
				  <div class="control">
	  				<label class="checkbox">
					  <input type="checkbox" name="<%=LoginServlet.REMEMBER_PARAM%>" value="true"/>
					  <span>Auf diesem Gerät angemeldet bleiben (setzt ein <a href="<%=request.getContextPath()%>/datenschutz.jsp">Cookie</a>)</span>
					</label>
				  </div>
				</div>

				<div class="field is-grouped">
				  <p class="control">
				    <button class="button is-primary" type="submit">
			          <span class="icon">
					      <i class="fa-solid fa-right-to-bracket"></i>
					  </span>
				      <span>Code anfordern</span>
				    </button>
				  </p>
				</div>
				
				<p>
					 Du erhälst einen Code an die angegebene E-Mail-Adresse, mit dem Du Dich anmelden kannst. 
					 Wenn Du schon einen PhoneBlock-Nutzernamen und ein Passwort hast, dann kannst Du Dich auch
					 mit diesem anmelden, siehe unten.  
				</p>
			</div>
			</div>
			</form>
		</div>
	</nav>
	
<%
	boolean loginError = request.getAttribute(LoginServlet.LOGIN_ERROR_ATTR) != null;
	String userActive = loginError ? "is-active" : "";
%>

	<nav class="panel">
		<p class="panel-heading">
			<a href="#loginForm" data-action="collapse"><i class="fas fa-user"></i> <span>Mit PhoneBlock-Nutzernamen anmelden</span></a>
		</p>
		<div id="loginForm" class="is-collapsible <%=userActive%>">
			<div class="panel-block">
	  		<div class="content">
				<form action="<%=request.getContextPath()%><%=LoginServlet.PATH %>" method="post">
				<%
					if (location != null) {
				%>
			    <input type="hidden" name="<%=LoginServlet.LOCATION_ATTRIBUTE%>" value="<%= JspUtil.quote(location) %>">
				<%
					}
				%>
				<div class="field">
				  <p class="control has-icons-left has-icons-right">
				    <input class="input" type="text" placeholder="Benutzername" name="<%=LoginServlet.USER_NAME_PARAM%>">
				    <span class="icon is-small is-left">
				      <i class="fa-solid fa-user"></i>
				    </span>
				    <span class="icon is-small is-right">
				      <i class="fas fa-check"></i>
				    </span>
				  </p>
				  <p class="help">Verwende den Benutzernamen, den Du bei Deiner Anmeldung mit Google oder E-Mail-Adresse erhalten hast.</p>
				</div>
				<div class="field">
				  <p class="control has-icons-left">
				    <input class="input" type="<%=LoginServlet.PASSWORD_PARAM%>" placeholder="PhoneBlock-Passwort" name="password">
				    <span class="icon is-small is-left">
				      <i class="fas fa-lock"></i>
				    </span>
				  </p>
<% if (loginError) {%>
					  <p class="help is-danger">Die Anmeldedaten stimmen nicht überein, bitte überprüfe die Eingaben und versuche es noch einmal.</p>
<% } else { %>
					  <p class="help">
					  	Das Passwort wurde Dir nach der ersten Anmeldung angezeigt. Wenn Du das Passwort nicht mehr zur Hand hast, 
					  	dann melde Dich einfach mit Google oder Deiner E-Mail-Adresse an, siehe oben.
					  </p>
<% } %>
				</div>

				<div class="field">
				  <div class="control">
	  				<label class="checkbox">
					  <input type="checkbox" name="<%=LoginServlet.REMEMBER_PARAM%>" value="true"/>
					  <span>Auf diesem Gerät angemeldet bleiben (setzt ein <a href="<%=request.getContextPath()%>/datenschutz.jsp">Cookie</a>)</span>
					</label>
				  </div>
				</div>
				
				<div class="field is-grouped">
				  <p class="control">
				    <button class="button is-primary" type="submit">
			          <span class="icon">
					      <i class="fa-solid fa-right-to-bracket"></i>
					  </span>
				      <span>Anmelden</span>
				    </button>
				  </p>
				</div>
				</form>
			</div>
			</div>
		</div>
	</nav>	
</section>

<jsp:include page="footer.jspf"></jsp:include>

</body>
</html>