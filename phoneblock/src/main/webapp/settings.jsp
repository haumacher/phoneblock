<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.UIProperties"%>
<%@page import="de.haumacher.phoneblock.app.AssignContributionServlet"%>
<%@page import="java.text.NumberFormat"%>
<%@page import="de.haumacher.phoneblock.db.settings.Contribution"%>
<%@page import="de.haumacher.phoneblock.db.DBContribution"%>
<%@page import="java.util.Date"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="java.text.DateFormat"%>
<%@page import="de.haumacher.phoneblock.db.settings.AuthToken"%>
<%@page import="de.haumacher.phoneblock.db.DBAuthToken"%>
<%@page import="de.haumacher.phoneblock.app.PBLogoutFilter"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
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
<link rel="canonical" href="https://phoneblock.net/phoneblock/" />
<jsp:include page="head-content.jspf"></jsp:include>

<link rel="stylesheet" href="<%= request.getContextPath() %><%=UIProperties.BULMA_CALENDAR_PATH %>/dist/css/bulma-calendar.min.css">
<script type="text/javascript" src="<%=request.getContextPath() %><%=UIProperties.BULMA_CALENDAR_PATH %>/dist/js/bulma-calendar.min.js"></script>

</head>

<%
	String userName = LoginFilter.getAuthenticatedUser(session);
	Object token = RegistrationServlet.getPassword(session);
%>
<body>
<jsp:include page="header.jspf"></jsp:include>

<% if (userName == null) { %>
<section class="section">
	<div class="content">
		<h1>Einstellungen</h1>
		
		<p>
			Um deine persönlichen Einstellungen zu bearbeiten, musst Du Dich <a href="<%= request.getContextPath()%>/login.jsp<%= LoginServlet.locationParamFirst(request) %>">anmelden</a>.
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
				Willkommen <%= JspUtil.quote(settings.getDisplayName()) %><% if (settings.getEmail() != null) { %>				
				(<%= JspUtil.quote(settings.getEmail()) %>)<% } %>.
			</p>
			
			<form action="<%= request.getContextPath() %><%=SettingsServlet.PATH%>" method="post">

<% if (token != null) { %>
			<article class="message is-info">
			  <div class="message-header">
			    <p>Deine Zugangsdaten</p>
			  </div>
			  
			  <div class="message-body">

				<div class="field">
				  <label class="label">Internetadresse des CardDAV-Servers</label>
				  <div class="control"><code id="url">https://phoneblock.net<%=request.getContextPath() %>/contacts/</code><a id="url_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></div>
				</div>

<% } %>			
				<div class="field">
				  <label class="label">Benutzername</label>
				  <div class="control"><code id="login"><%= JspUtil.quote(userName) %></code><a id="login_" title="In die Zwischenablage kopieren." class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></div>
				  <p class="help">
				  	Diesen Wert musst du als Benutzernamen für den <a href="<%=request.getContextPath()%>/setup.jsp">Abruf der Blocklist</a> eintragen.
				  	Dein Passwort wurde Dir nach Deiner ersten Anmeldung angezeigt. Wenn du dieses nicht mehr weißt, dann kannst Du unten auf dieser Seite
				  	ein <a href="#resetPassword">neues Passwort erstellen lassen</a>. Aber Vorsicht: Das alte Passwort wird dadurch ungültig. 
				  </p>
				</div>
					
<% if (token != null) { %>
				<div class="field">
				  <label class="label">Passwort</label>
				  <div class="control"><code id="passwd"><%= JspUtil.quote(token) %></code><a id="passwd_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></div>
				  <p class="help">Dieses Passwort musst Du für die <a href="<%=request.getContextPath()%>/setup.jsp">Einrichtung des Telefonbuchs</a> oder für die <a href="<%=request.getContextPath()%><%=SettingsServlet.PATH%>">Anmeldung an dieser Webseite</a> verwenden. </p>
				  <p class="help">
				  	Bitte notiere Dir das Passwort (oder speichere es am besten in einem <a href="https://keepass.info/">Passwort-Manager</a>), 
				  	denn es wird nur solange angezeigt bis Du Dich abmeldest, oder Deine Sitzung abläuft.
				  </p>
				</div>
			  </div>
			</article>
<% } %>			

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
			    <option value="2" <%if (settings.getMinVotes() == 2) {%>selected="selected"<%}%>>2 (sofort sperren)</option>	
			    <option value="4" <%if (settings.getMinVotes() == 4) {%>selected="selected"<%}%>>4 (Bestätigungen abwarten)</option>	
			    <option value="10" <%if (settings.getMinVotes() == 10) {%>selected="selected"<%}%>>10 (erst wenn sicher)</option>	
			    <option value="100" <%if (settings.getMinVotes() == 100) {%>selected="selected"<%}%>>100 (nur Top-Spammer)</option>	
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
List<? extends AuthToken> explicitTokens = (List<? extends AuthToken>) request.getAttribute("explicitTokens");
%>

	<div class="content">
	<h2 id="myAPIKeys">Deine API-Keys</h2>
	<p>
		Wenn du andere Anwendungen verwendest, um Telefonnummern mit PhoneBlock zu überprüfen 
		(z.B. <a href="https://f-droid.org/packages/spam.blocker/">SpamBlocker</a> 
		oder <a href="https://spamblockup.jimdofree.com/">SpamBlockUp</a>), 
		dann benötigst Du einen API-Key. Für das Blocklist-Telefonbuch in Deiner Fritz!Box verwendest Du einfach Deinen 
		PhoneBlock-Nutzernamen und PhoneBlock-Passwort, hierfür benötigst Du also keinen API-Key.
	</p>
	
<% if (explicitTokens.isEmpty()) { %>
	<p>
		Du hast noch keine API-Keys erzeugt.
	</p>	
<% } else { %>
	<form action="<%= request.getContextPath() %><%=SettingsServlet.PATH%>?action=deleteAPIKeys" method="post">
	<table>
		<tr>
			<th>
			</th>
			<th>
				Name
			</th>
			<th>
				Erzeugt
			</th>
			<th>
				Letzte Verwendung
			</th>
			<th>
				Gerät
			</th>
		</tr>
<%
	DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	for (AuthToken authToken : explicitTokens) {
%>
		<tr>
			<td>
				<input type="checkbox" name="<%=SettingsServlet.KEY_ID_PREFIX%><%= authToken.getId() %>"/>
			</td>
			<td>
				<%= JspUtil.quote(authToken.getLabel()) %>
			</td>
			<td>
				<%= JspUtil.quote(dateFormat.format(new Date(authToken.getCreated()))) %>
			</td>
			<td>
<%
	if (authToken.getLastAccess() == 0) {
%>
				Nicht verwendet.
<%		
	} else {
%>
				<%= JspUtil.quote(dateFormat.format(new Date(authToken.getLastAccess()))) %>
<%		
	}
%>
			</td>
			<td>
				<%= JspUtil.quote(authToken.getUserAgent()) %>
			</td>
		</tr>
<%
	}
%>	
	</table>
	
	<div class="field is-grouped">
	  <p class="control">
	    <button class="button is-danger" type="submit">
	      Markierte löschen
	    </button>
	  </p>
	</div>
	
	</form>
<% } %>
	</div>
</section>

<section class="section">
	<div class="content">
	<h2 id="createAPIKey">API-Key erzeugen</h2>

	<form action="<%= request.getContextPath() %><%=SettingsServlet.PATH%>?action=createAPIKey" method="post">

	<div class="field">
	  <label class="label">Bemerkung für Verwendung</label>
	  <p class="control has-icons-left">
	    <input class="input" type="text" placeholder="Nutzung für..." name="<%=SettingsServlet.API_KEY_LABEL_PARAM%>">
	  </p>
	  <p class="help">
	  Du kannst hier eine Hinweis eingeben, wofür Du den API-Key erzeugt hast, z.B. "SpamBlocker auf Omas Handy.".
	  </p>
	</div>
		
	<div class="field is-grouped">
	  <p class="control">
	    <button class="button is-primary" type="submit">
	      API-Key erzeugen
	    </button>
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
	
	<form action="<%= request.getContextPath() %><%=SettingsServlet.PATH%>?action=lists" method="post">

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
	
	<form action="<%= request.getContextPath() %><%=SettingsServlet.PATH%>?action=lists" method="post">

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
	<form action="<%= request.getContextPath() %><%=SettingsServlet.PATH%>?action=lists" method="post">

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
<%
List<DBContribution> contributions = (List<DBContribution>) request.getAttribute("contributions");
%>
<div class="content">
	<h2 id="<%= AssignContributionServlet.SECTION_CONTRIBUTIONS%>">Deine Spenden für den Betrieb von PhoneBlock</h2>
	
	<p>
		Wenn Du eine <a href="<%= request.getContextPath() %>/support.jsp">Spende für den Betrieb von PhoneBlock</a> gemacht hast, 
		dann wird diese hier (nach einiger Zeit) aufgelistet. 
		Damit das klappt, wäre es nett, wenn Du bei der Überweisungsnachricht die ersten paar Zeichen von Deinem Nutzernamen
		mitschicken würdest, also z.B. <code id="purpose">PhoneBlock-<%= userName.substring(0, 13)%></code><span id="purpose_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></span>.
		Beiträge über das GitHub-Sponsor-Programm können hier leider nicht aufgelistet werden. 
	</p>
	
<% if (contributions.isEmpty()) { %>
	<p>
		Keine Spendenbeiträge gefunden.
	</p>
<% } else { %>
	<table class="table">
	<thead>
	<tr>
	<th>Datum</th>
	<th>Nachricht</th>
	<th>Transaktionsnummer</th>
	<th>Betrag</th>
	</tr>
	</thead>
<% DateFormat dateFormat = DateFormat.getDateInstance(); %>
<% NumberFormat amountFormat = NumberFormat.getCurrencyInstance(); %>
	<tbody>
<% for (Contribution contribution : contributions) { %>
	<tr>
	<th><%= JspUtil.quote(dateFormat.format(new Date(contribution.getReceived()))) %></th>
	<th><%= JspUtil.quote(contribution.getMessage()) %></th>
	<th><%= JspUtil.quote(contribution.getTx()) %></th>
	<th><%= JspUtil.quote(amountFormat.format(contribution.getAmount() / 100.0)) %>
	</tr>
<% } %>	
	</tbody>
	</table>
	
	<p>
	Vielen lieben Dank, dass Du Dich an den Kosten des Betriebs von PhoneBlock beteiligst!
	</p>	
<% } %>
</div>

<nav class="panel is-info">
	<p class="panel-heading"><a href="#contribForm" data-action="collapse"><i class="fa-solid fa-eraser"></i> <span>Zahlung vermisst?</span></a></p>
	<div id="contribForm" class="is-collapsible">

	<form action="<%= request.getContextPath() %><%= AssignContributionServlet.PATH %>" method="post" enctype="application/x-www-form-urlencoded">
	<div class="panel-block">
	<div class="content">
		<p>
		Du vermisst eine Zahlung? Hier kannst Du nach einer Zahlung suchen. Wenn du mit PayPal bezahlt hast, dann 
		gib den Transaktionscode ein. Bei einer Banküberweisung, versuche es mit Deinem vollständigen Namen (den 
		Deine Bank als Absender verwendet) und dem Datum der Überweisung:
		</p>
	
		<div class="field">
		  <label class="label">Transaktionscode</label>
		  <p class="control has-icons-left">
		    <input class="input" type="text" placeholder="13121212H7878787W" name="<%= AssignContributionServlet.CONTRIB_TX%>">
		    <span class="icon is-small is-left"><i class="fa-solid fa-signature"></i></span>
		  </p>
		  <p class="help">
		  Der PayPal Transaktionscode. Du findest diesen in den Details Deiner Überweisung. Bei einer Banküberweisung leer lassen.
		  </p>
		</div>
	
		<div class="field">
		  <label class="label">Absender</label>
		  <p class="control has-icons-left">
		    <input class="input" type="text" placeholder="Max Mustermann" name="<%= AssignContributionServlet.CONTRIB_NAME%>">
		    <span class="icon is-small is-left"><i class="fa-regular fa-user"></i></span>
		  </p>
		  <p class="help">
		  Bei einer Banküberweisung der Name des Kontoinhabers, der die Überweisung getätigt hat. Bitte 
		  gib auch noch das Datum der Überweisung unten an.
		  </p>
		</div>
	
		<div class="field">
		  <label class="label">Datum</label>
		  <p class="control has-icons-left">
		    <input class="input" type="date" name="<%= AssignContributionServlet.CONTRIB_DATE%>">
		  </p>
		  <p class="help">
		  Das Datum der Banküberweisung. Bitte gib auch oben noch den vollständigen Namen des Kontoinhabers an, der die Überweisung getätigt hat.
		  </p>
		</div>
	</div>
	</div>

	<div class="panel-block">
		<button class="button is-medium is-primary" type="submit">
		    <span class="icon">
				<i class="fa-solid fa-magnifying-glass-dollar"></i>
		    </span>
			<span>Beitrag suchen</span>
		</button>
	</div>

	</form>
  	</div>
</nav>

</section>

<section class="section">
	<div class="content">
	<h2>Gefahrenzone</h2>
	<p>Nutze diese Funktionalität mit Bedacht. Du kannst Deine PhoneBlock-Installation damit kaputt machen!</p>
	</div>
		
<nav class="panel is-warning" id="resetPassword">
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
	<p class="panel-heading"><a href="#logoutForm" data-action="collapse"><i class="fa-solid fa-right-from-bracket"></i> <span>An allen Geräten abmelden</span></a></p>
	<div id="logoutForm" class="is-collapsible">
		<form action="<%= request.getContextPath() %>/logout?url=<%=request.getContextPath()%>/&all=true" method="post" enctype="application/x-www-form-urlencoded">
  		<div class="panel-block">
	  		<div class="content">
	  			<p>
	  			Meldet Dich bei allen Geräten ab, bei denen Du beim Login die Option "Auf diesem Gerät angemeldet bleiben" 
	  			aktiviert hast. Bei Deinem nächsten Besuch musst Du Dich auf allen Geräten erneut anmelden. Nutze diese 
	  			Abmelden-Funktion, wenn Du aus Versehen auf einem öffentlichen PC die Funktion "angemeldet bleiben"
	  			aktiviert hast.
	  			</p>
	  		</div>
	  	</div>
	  	
  		<div class="panel-block">
			<button class="button is-medium is-fullwidth is-danger" type="submit">
			    <span class="icon">
					<i class="fa-solid fa-right-from-bracket"></i>
			    </span>
				<span>Überall abmelden</span>
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