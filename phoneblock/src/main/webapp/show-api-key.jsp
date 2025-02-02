<%
	AuthToken apiKey = (AuthToken) request.getAttribute("apiKey");
	if (apiKey == null) {
		response.sendRedirect(request.getContextPath() + SettingsServlet.PATH);
		return;
	}
%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.util.ServletUtil"%>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.db.settings.AuthToken"%>
<!DOCTYPE html>
<html>
<%
	request.setAttribute("title", "Dein neuer API key - PhoneBlock");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Neuer API Key</h1>
		
		<form action="<%= request.getContextPath() %><%=SettingsServlet.PATH%>" method="get">

		<article class="message is-info">
		  <div class="message-header">
		    <p>Dein neuer API wurde erzeugt. Bitte kopiere den Wert, denn er kann nicht mehr angezeigt werden, sobald Du diese Seite verlässt.</p>
		  </div>

		  <div class="message-body">
			<div class="field">
			  <label class="label">Bemerkung</label>
			  <div class="control"><%= JspUtil.quote(apiKey.getLabel()) %></div>
			</div>

			<div class="field">
			  <label class="label">API Key</label>
			  <div class="control"><code id="apiKey"><%= JspUtil.quote(apiKey.getToken())%></code><a id="apiKey_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></div>
			</div>
		  </div>
		</article>
		
		<div class="field is-grouped">
		  <p class="control">
		    <button class="button is-primary" type="submit">
		      Weiter
		    </button>
		  </p>
		</div>
		
		</form>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>

</body>
</html>