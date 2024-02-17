<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
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

<h1>Registrierung</h1>

<p>
	Um PhoneBlock in Deinem Internet-Router zu installieren, benötigst Du einen Benutzernamen und ein Passwort. Beides 
	erhältst Du bei der Registrierung. In der <a href="<%= request.getContextPath() %>/setup.jsp">Installationsanleitung</a>
	erfährst Du, wie Du anschließend die Blocklist abrufen und Anrufer mit diesen Telefonnummern blockieren kannst.  
</p>

</div>

<nav class="panel">
	<p class="panel-heading"><a href="<%=request.getContextPath()%>/oauth/login?force_client=Google2Client"><i class="fa-brands fa-google"></i> <span>Mit Google registrieren</span></a></p>
</nav>

<nav class="panel">
	<p class="panel-heading"><a href="<%=request.getContextPath()%>/oauth/login?force_client=FacebookClient"><i class="fa-brands fa-facebook-f"></i> <span>Mit Facebook registrieren</span></a></p>
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
						<a href="<%=request.getContextPath()%>/signup.jsp">Nochmal probieren</a>.								
					</p>
<%
			  	}
%>
			</div>		
					
			<div class="buttons is-right">
		    	<button class="button is-link" type="submit">Registrieren</button>
			</div>
			
			<p>
				 Wenn Du schon einen PhoneBlock-Account hast, dann <a href="<%=request.getContextPath()%>/login.jsp">melde Dich an</a>! 
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