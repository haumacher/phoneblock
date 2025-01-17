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
			Um PhoneBlock als <a href="<%=request.getContextPath() %>/setup.jsp">Blocklisten-Telefonbuch</a>, 
			oder <a href="<%=request.getContextPath() %>/anrufbeantworter/">intelligenten Anrufbeantworter</a> einzurichten, 
			musst Du Dich zuvor hier anmelden.
		</p>

		<p> 
			Nach der Anmeldung kannst Du in den <a href="<%=request.getContextPath() %><%=SettingsServlet.PATH %>">PhoneBlock-Einstellungen</a>
			 z.B. festlegen, ab welcher Konfidenz eine Telefonnummer zu Deiner
			Blocklist hinzugefügt wird oder welche Länge Deine Blocklist höchstens haben darf.
		</p>

		<p>
			 Auch die Verwaltung Deiner <a href="<%=request.getContextPath() %>/ab/">PhoneBlock-Anrufbeantworter</a>
			 erfordert eine Anmeldung.
		</p> 
	</div>

	<jsp:include page="login-forms.jspf">
		<jsp:param name="web" value="true" />
	</jsp:include>

</section>

<jsp:include page="footer.jspf"></jsp:include>

</body>
</html>