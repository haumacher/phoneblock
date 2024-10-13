<!DOCTYPE html>
<%@page import="java.net.URLEncoder"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.app.oauth.PhoneBlockConfigFactory"%>
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

<h1>Registrierung</h1>

<p>
	Um PhoneBlock in Deinem Internet-Router zu installieren, benötigst Du einen Benutzernamen und ein Passwort. Beides 
	erhältst Du bei der Registrierung. In der <a href="<%= request.getContextPath() %>/setup.jsp">Installationsanleitung</a>
	erfährst Du, wie Du anschließend die Blocklist abrufen und Anrufer mit diesen Telefonnummern blockieren kannst.  
</p>

</div>

	<%
		String location = LoginServlet.location(request);
		String locationParam = LoginServlet.locationParam(request);
		String locationParamFirst = LoginServlet.locationParamFirst(request);
	%>

<nav class="panel">
	<p class="panel-heading"><a href="<%=request.getContextPath()%>/oauth/login?force_client=<%=PhoneBlockConfigFactory.GOOGLE_CLIENT%>"><i class="fa-brands fa-google"></i> <span>Mit Google registrieren</span></a></p>
</nav>

<%
	Object message = request.getAttribute("message");
	String active = message != null ? "is-active" : "";
%>
<nav class="panel">
	<p class="panel-heading"><a href="#registerForm" data-action="collapse"><i class="fas fa-envelope"></i> <span>Mit E-Mail registrieren</span></a></p>
	<div id="registerForm" class="is-collapsible <%=active%>">
		<form action="<%= request.getContextPath() %>/verify-email" method="post" enctype="application/x-www-form-urlencoded">
  		<div class="panel-block">
  		<div class="content">
			<%
				if (location != null) {
			%>
		    <input type="hidden" name="<%=LoginServlet.LOCATION_ATTRIBUTE%>" value="<%= JspUtil.quote(location) %>">
			<%
				}
			%>
			<div class="field">
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
					<p class="help is-danger">
						<%= JspUtil.quote(request.getAttribute("message")) %>
						<a href="<%=request.getContextPath()%>/signup.jsp<%=locationParamFirst%>">Nochmal probieren</a>.								
					</p>
<%
			  	}
%>
			</div>		
					
			<div class="buttons is-right">
		    	<button class="button is-link" type="submit">Registrieren</button>
			</div>
			
			<p>
				 Wenn Du schon einen PhoneBlock-Account hast, dann <a href="<%=request.getContextPath()%>/login.jsp<%=locationParamFirst%>">melde Dich an</a>! 
			</p>
		</div>
		</div>
		</form>
	</div>
</nav>

</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>