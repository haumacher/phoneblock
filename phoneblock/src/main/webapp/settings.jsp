<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="java.util.List"%>
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
				<div class="select">			  
			    <select class="input" name="maxLength">
			    	<option value="1000" <% if (settings.getMaxLength() == 1000){%>selected="selected"<%}%>>1000 (klein für ganz alte Fritz!Boxen)</option>
			    	<option value="2000" <% if (settings.getMaxLength() == 2000){%>selected="selected"<%}%>>2000 (empfohlen für die meisten Fritz!Boxen)</option>
			    	<option value="3000" <% if (settings.getMaxLength() == 3000){%>selected="selected"<%}%>>3000 (funktioniert z.B. noch auf FRITZ!Box 7590)</option>
			    	<option value="4000" <% if (settings.getMaxLength() == 4000){%>selected="selected"<%}%>>4000 (beachte die Hinweise unten)</option>
			    	<option value="5000" <% if (settings.getMaxLength() == 5000){%>selected="selected"<%}%>>5000 (riesig, beachte die Hinweise unten)</option>
			    	<option value="6000" <% if (settings.getMaxLength() == 6000){%>selected="selected"<%}%>>6000 (extrem, Vorsicht, Deine Fritz!Box kann abstürzen)</option>
			    </select>
			    </div>
			    <span class="icon is-small is-left">
			      <i class="fas fa-ruler-vertical"></i>
			    </span>
			  </div>
			  <p class="help">Wenn Du Probleme beim Update der Blocklist hast, kannst Du hier die Größe Deiner Blocklist reduzieren. 
			  AVM empfiehlt Telefonbücher mit höchstens 1000 Einträgen zu befüllen. Die FRITZ!Box 7590 verkraftest z.B. bis zu
			  3000 Einträge in einem Telefonbuch.</p>
			  
			  <p class="help">
			  <b>Achtung:</b> Wenn Du ausprobieren willst, wie viele Einträge Deine Box in einem Telefonbuch speichern kann, dann gehe folgendermaßen vor: 
			  Beginne mit der Standardeinstellung von 2000 Einträgen. Synchronisiere die Blocklist und lass Dir das Blocklist-Telefonbuch anzeigen. 
			  Scrolle ganz ans Ende und lass Dir die Druckvorschau der Blocklist anzeigen und merke Dir die Anzahl der Seiten, die ausgedruckt würden.
			  Erhöhe jetzt die Blocklist-Größe um einen Schritt, synchronisiere die Blocklist neu und lass Dir wieder die Druckvorschau der Blocklist 
			  anzeigen. Wenn sich die Seitenanzahl entsprechend erhöht hat, dann kann Deine Box mit dieser Größe umgehen, probiere die nächste Größe. 
			  Wenn sich die Seitenanzahl der Druckvorschau nicht oder nicht entsprechend erhöht hat, dann nimm die letzt kleinere Größe der Blocklist. 
			  Interessanterweise meldet die Fritz!Box keinen Fehler, wenn ein Telefonbuch zu groß wird, sondern synchronisiert entweder einfach nicht
			  oder lässt irgendwelche Nummern einfach weg. Bei einer zu großen Telefonbuchgröße habe ich Boxen schon einfach abstürzen sehen - sei
			  also vorsichtig.
			  </p>
			</div>
			
			<div class="field">
			  <label class="label">Mindest-Konfidenz</label>
			  <div class="control has-icons-left">
				<div class="select">			  
			    <select class="input" name="minVotes">
			    <option value="4" <%if (settings.getMinVotes() == 4) {%>selected="selected"<%}%>>4 (sofort sperren)</option>	
			    <option value="8" <%if (settings.getMinVotes() == 8) {%>selected="selected"<%}%>>8</option>	
			    <option value="20" <%if (settings.getMinVotes() == 20) {%>selected="selected"<%}%>>20</option>	
			    <option value="100" <%if (settings.getMinVotes() == 100) {%>selected="selected"<%}%>>100 (nur sperren wenn ganz sicher)</option>	
			    </select>
			    </div>
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
			
			<div class="field">
			  <label class="checkbox">
			    <input type="checkbox" <%if (settings.isWildcards()) {%>checked="checked"<%}%> name="wildcards">
				Nummern mit "*" zusammenfassen
			  </label>
			  <p class="help">Wenn Du dies Option auswählst, werden in Deiner Blocklist nebeneinander liegende Nummern 
			  zu einer Nummer mit Wildcard ("*") zusammengefasst. Viele Profi-Spammer haben einen Mehrgeräteanschluss 
			  und nutzen einen ganzen Nummernblock als Absender. Mit dieser Option werden automatisch alle diese Nummern 
			  blockiert und deine Blocklist kann mehr Spam-Nummern aufnehmen. Die Fritz!Box unterstützt Telefonbücher mit 
			  Wildcard-Nummern und blockiert dann den ganzen angegebenen Nummernbereich. Auf anderen Geräten kannst Du
			  aber Wildcard-Nummer möglicherweise nicht verwenden.</p>
			</div>
			
			<div class="field is-grouped">
			  <p class="control">
			    <button class="button is-primary" type="submit">
			      Speichern
			    </button>
			  </p>
			  <p class="control">
			    <a class="button " href="<%= request.getContextPath() + SettingsServlet.PATH %>">
			      Verwerfen
			    </a>
			  </p>
			</div>
			</form>
	</div>
</section>

<section class="section">
<%
List<String> blacklist = (List<String>) request.getAttribute("blacklist");
%>
	<div class="content">
	<h2 id="blacklist">Deine Blacklist</h2>
	
	<form action="<%= request.getContextPath() %>/settings?action=lists" method="post">

<% if (blacklist.isEmpty()) { %>
	<p>Du hast keine Nummern explizit gesperrt. Um eine Nummer zu sperren, suche die Nummer über das Suchfeld oben und schreibe einen negativen Kommentar, oder mach in Deiner Fritz!Box einen Telefonbucheintrag für die Nummer in der Blocklist.</p>
<% } else { %>
	<p>Diese Nummern hast Du explizit gesperrt. Eine versehentlich eingetragene Sperre kannst Du hier aufheben:</p>
	<% for (String number : blacklist) { %>
	<div class="field">
		<label class="checkbox">
			<input type="checkbox" name="bl-<%=number %>" /> <%=number %>
		</label>
	</div>
	<% } %>

	<div class="field is-grouped">
	  <p class="control">
	    <button class="button is-danger" type="submit">
	      Löschen
	    </button>
	  </p>
	</div>
<% } %>
	
	</form>
	
	</div>
</section>

<section class="section">
<%
List<String> whitelist = (List<String>) request.getAttribute("whitelist");
%>
	<div class="content">
	<h2 id="whitelist">Deine Whitelist</h2>
	
	<form action="<%= request.getContextPath() %>/settings?action=lists" method="post">

<% if (whitelist.isEmpty()) { %>
	<p>Du hast keine Nummern von der Sperrung ausgenommen.</p>
<% } else { %>
	<p>Diese Nummern hast Du explizit von der Sperrung ausgenommen:</p>

<% for (String number : whitelist) { %>
	<div class="field">
		<label class="checkbox">
			<input type="checkbox" name="wl-<%=number %>" /> <%=number %>
		</label>
	</div>
<% } %>
	
	<div class="field is-grouped">
	  <p class="control">
	    <button class="button is-danger" type="submit">
	      Löschen
	    </button>
	  </p>
	</div>
<% } %>
	
	</form>
	</div>
	
	<div class="content">
	<form action="<%= request.getContextPath() %>/settings?action=lists" method="post">

	<div class="field">
	  <label class="label">Ausnahme hinzufügen</label>
	  <p class="control has-icons-left">
	    <input class="input" type="tel" placeholder="Neue Ausnahme" name="add-wl">
	    <span class="icon is-small is-left">☎</span>
	  </p>
	  <p class="help">
	  Du kannst hier eine oder mehrere Telefonnummern mit Komma getrennt in beliebigem Format eingeben: 07041-123456789, +49171123456789, 0034 123456789 
	  </p>
	</div>
	
	<div class="field is-grouped">
	  <p class="control">
	    <button class="button is-primary" type="submit">
	      Hinzufügen
	    </button>
	  </p>
	</div>
	
	</form>
	</div>
</section>

<section class="section">
	<div class="content">
	<h2>Gefahrenzone</h2>
	<p>Nutze diese Funktionalität mit Bedacht. Du kannst Deine PhoneBlock-Installation damit kaputt machen!</p>
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