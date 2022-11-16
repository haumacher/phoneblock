<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@page import="de.haumacher.phoneblock.app.DeleteAccountServlet"%>
<%@page import="de.haumacher.phoneblock.app.ResetPasswordServlet"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.db.settings.UserSettings"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<html>
<%
	request.setAttribute("title", "Persönliche Einstellungen - PhoneBlock");
%>
<head>
<link rel="canonical" href="https://phoneblock.haumacher.de/phoneblock/" />
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<%
	String userName = LoginFilter.getAuthenticatedUser(session);
%>
<body>
<jsp:include page="header.jspf"></jsp:include>

<% if (userName == null) { %>
<section class="section">
	<div class="content">
		<h1>Einstellungen</h1>
		
		<p>
			Um deine persönlichen Einstellungen zu bearbeiten, musst Du Dich <a href="<%= request.getContextPath()%>/login.jsp">anmelden</a>.
		</p>
	</div>
</section>
<% } else { %>
<section class="section">
	<div class="content">
		<h1>Einstellungen</h1>
			<%
				UserSettings settings = DBService.getInstance().getSettings(userName);
			%>
			<p>
				Wilkommen <%= JspUtil.quote(settings.getDisplayName()) %>.
			</p>
			
			<form action="<%= request.getContextPath() %>/settings" method="post">
			<div class="field">
			  <label class="label">Benutzername</label>
			  <div class="control has-icons-left">
			    <input class="input" type="text" value="<%= JspUtil.quote(userName)%>" name="userName" disabled="disabled">
			    <span class="icon is-small is-left">
			      <i class="fa-solid fa-user"></i>
			    </span>
			  </div>
			  <p class="help">Diesen Wert musst du als Benutzernamen für den <a href="<%=request.getContextPath()%>/setup.jsp">Abruf der Blocklist</a> eintragen. </p>
			</div>
			
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
	</div>
</section>

<section class="section">
	<div class="content">
	<h2>Gefahrenzone</h2>
	<p>Nutze diese Funktionalität mit Bedacht. Du kannst Deine PhonBlock-Installation damit kaputt machen!</p>
	</div>
		
<nav class="panel is-warning">
	<p class="panel-heading"><a href="#resetForm" data-action="collapse"><i class="fa-solid fa-eraser"></i> <span>Neues Passwort erzeugen</span></a></p>
	<div id="resetForm" class="is-collapsible">
		<form action="<%= request.getContextPath() %><%= ResetPasswordServlet.PATH %>" method="post" enctype="application/x-www-form-urlencoded">
  		<div class="panel-block">
	  		<div class="content">
	  			<p>
	  			Vorsicht, Dein altes PhoneBlock-Passwort wird hierdurch ungültig. Du musst danach das neue Passwort in 
	  			den Einstellungen Deiner Fritz!Box oder Deines Mobiltelefons eintragen, damit der Abruf der Blocklist 
	  			weiterhin funktioniert.
	  			</p>
	  		</div>
	  	</div>
	  	
  		<div class="panel-block">
			<button class="button is-medium is-fullwidth is-danger" type="submit">
			    <span class="icon">
					<i class="fa-solid fa-eraser"></i>
			    </span>
				<span>Passwort zurücksetzen</span>
			</button>
  		</div>
  		</form>
  	</div>
</nav>
	
<nav class="panel is-warning">
	<p class="panel-heading"><a href="#quitForm" data-action="collapse"><i class="fa-solid fa-power-off"></i> <span>Account löschen</span></a></p>
	<div id="quitForm" class="is-collapsible">
		<form action="<%= request.getContextPath() %><%= DeleteAccountServlet.PATH %>" method="post" enctype="application/x-www-form-urlencoded">
  		<div class="panel-block">
	  		<div class="content">
	  			<p>
	  			Vorsicht, alle Deine Daten werden gelöscht. Der Abruf der Blocklist von allen Deinen Geräten ist danach nicht mehr möglich!
	  			</p>
	  		</div>
	  	</div>
	  	
  		<div class="panel-block">
			<button class="button is-medium is-fullwidth is-danger" type="submit">
			    <span class="icon">
					<i class="fa-solid fa-power-off"></i>
			    </span>
				<span>Zugang löschen, keine weitere Sicherheitsabfrage!</span>
			</button>
  		</div>
  		</form>
  	</div>
</nav>

</section>
<% } %>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>