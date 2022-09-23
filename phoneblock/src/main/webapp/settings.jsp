<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.db.settings.UserSettings"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8" session="true"%>
<html>
<head>
<link rel="canonical" href="https://phoneblock.haumacher.de/phoneblock/" />
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<%
	String userName = LoginFilter.getAuthenticatedUser(session);
%>
<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">

		<h1>Einstellungen</h1>
		
		<% if (userName == null) { %>
			<p>
				Um deine persönlichen Einstellungen zu bearbeiten, musst Du Dich <a href="<%= request.getContextPath()%>/login.jsp">anmelden</a>.
			</p>
		<% } else { %>
			<p>
				Wilkommen <%= userName %>.
			</p>
			
			<form action="<%= request.getContextPath() %>/settings" method="post">
			<%
				UserSettings settings = DBService.getInstance().getSettings(userName);
			%>
			<div class="field">
			  <label class="label">Maximale Blocklist-Größe</label>
			  <div class="control has-icons-left">
			    <input class="input" type="number" value="<%= settings.getMaxLength()%>" name="maxLength">
			    <span class="icon is-small is-left">
			      <i class="fas fa-ruler-vertical"></i>
			    </span>
			  </div>
			  <p class="help">Wenn Du Probleme beim Update der Blocklist hast, kannst Du hier die Größe Deiner Blocklist beschränken. 
			  AVM empfiehlt Telefonbücher mit höchstens 1000 Einträgen zu befüllen. Aktuelle Fritz!Box-en verkraften aber bis zu
			  2000 Einträgen in einem Telefonbuch. </p>
			</div>
			
			<div class="field">
			  <label class="label">Mindest-Konfidenz</label>
			  <div class="control has-icons-left">
			    <input class="input" type="number" value="<%= settings.getMinVotes()%>" name="minVotes">
			    <span class="icon is-small is-left">
			      <i class="fas fa-phone-volume"></i>
			    </span>
			  </div>
			  <p class="help">
			  Je größer Du die Zahl wählst, desto mehr Beschwerden müssen für eine Telefonnummer 
			  vorliegen, bevor sie auf Deiner persönliche Blocklist landet und umso größer ist die Wahrscheinlichkeit, 
			  dass Dich auch ein Anruf von einer neuen Spam-Nummer trifft. Jede Beschwerde zählt 2 zur Konfidenz dazu. 
			  </p>
			</div>
			
			<div class="field is-grouped is-grouped-right">
			  <p class="control">
			    <button class="button is-primary" type="submit">
			      Speichern
			    </button>
			  </p>
			  <p class="control">
			    <a class="button " href="<%= request.getContextPath() %>/settings.jsp">
			      Verwerfen
			    </a>
			  </p>
			</div>
			</form>

			</form>
		<% } %>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>