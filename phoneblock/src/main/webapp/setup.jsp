<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock in Fritz!Box einrichten");
	request.setAttribute(LoginServlet.KEEP_LOCATION_AFTER_LOGIN, "true");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Installation als Telefonbuch</h1>
		
<%
		HttpSession session = request.getSession(false);
  		Object login = LoginFilter.getAuthenticatedUser(session);
  		Object token = RegistrationServlet.getPassword(session);
%>

<% if (token == null) { %>
		<p>
			Für die Installation der Blockliste benötigst Du einen <a href="<%=request.getContextPath()%>/link/fritzbox">"FRITZ!Box"
			Internetrouter von AVM</a> und einen PhoneBlock-Account. Es muss nicht unbedingt das neuste Modell
			sein, aber Du solltest prüfen, ob das aktuelle FRITZ!OS darauf
			installiert ist (07.29 oder neuer). Ist dies nicht der Fall, prüfe
			anhand der folgenden Installationsanleitung, ob Deine Version die notwendigen
			Optionen schon bietet.
		</p>
		
		<p>
			Beachte: Aufgrund technischer Beschränkungen der Fritz!Box kann nicht die gesamte Blockliste in ein
			Telefonbuch geladen werden. Daher bietet die Einrichtung eines 
			<a href="<%=request.getContextPath()%>/anrufbeantworter/">PhoneBlock-Anrufbeantworters</a> den 
			besseren Schutz vor SPAM-Anrufen.
		</p>

<%	if (login == null) { %>
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a href="<%=request.getContextPath() %>/login.jsp<%= LoginServlet.locationParamFirst(request) %>">
				<button class="button is-medium is-info is-fullwidth">PhoneBlock-Account erstellen</button>
			</a>
		  </div>
		</div>
<% 	} else { %>
		<p>
			Du bist bereits als <code><%= JspUtil.quote(login) %></code> angemeldet, prima! Du kannst sofort loslegen.
		</p>
<% 	} %>
<% } else { %>
<div class="columns">
  <div class="column">
	<div class="tile is-ancestor">
      <div class="tile is-parent">
      	<article class="tile is-child notification">
	        <p class="title">Deine Anmeldedaten</p>
	        <p class="subtitle">Die folgenden Daten benötigst Du für die Installation.</p>
	        <div class="content">
		
				<div class="field">
				  <label class="label">Internetadresse des CardDAV-Servers</label>
				  <div class="control"><code id="url">https://phoneblock.net<%=request.getContextPath() %>/contacts/</code><a id="url_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></div>
				</div>
				
				<div class="field">
				  <label class="label">Benutzername</label>
				  <div class="control"><code id="login"><%= JspUtil.quote(login) %></code><a id="login_" title="In die Zwischenablage kopieren." class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></div>
				</div>
				
				<div class="field">
				  <label class="label">Passwort</label>
				  <div class="control"><code id="passwd"><%= JspUtil.quote(token) %></code><a id="passwd_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a></div>
				</div>
			</div>
		</article>
	  </div>
    </div>
  </div>
</div>
<% } %>
		
		<h2>Melde Dich an Deiner FRITZ!Box an</h2>
		
		<p>
			Damit das funktioniert, muss Du Dich zuhause in deinem WLAN befinden, oder Dein Computer muss 
			direkt mit der FRITZ!Box über Kabel verbunden sein. Die FRITZ!Box fragt Dich als erstes nach 
			Deinem Passwort. Wenn Du Dich noch nie angemeldet hattest, dann findest Du dieses Passwort auf der 
			Unterseite des Gerätes.
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a href="http://fritz.box" target="_blank">
				<button class="button is-medium is-primary is-fullwidth">An FRITZ!Box anmelden</button>
			</a>
		  </div>
		</div>
		
		<h2>Erstelle ein neues Telefonbuch "Blocklist"</h2>
		
		<p>
			Damit Deine FRITZ!Box Spam-Anrufe blockieren kann, benötigt sie ein eigenes Telefonbuch, in das 
			alle Nummern von unerwünschten Anrufern eingetragen werden. Nenne Dieses Telefonbuch "Blocklist". 
			Hierfür
		</p>
		
		<ol>
			<li>Navigiere zuerst in die Rubrik "Telefonie", </li>
			<li>wähle den Menüpunkt "Telefonbuch" aus und</li>
			<li>klicke dann auf den Link "Neues Telefonbuch".</li>
		</ol>
		
		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="Anrufliste der Fritz!Box" src="<%=request.getContextPath() %>/02-navigate-to-addressbooks.png"/>
		  </div>
		</div>
		
		<h2>Trage die Anmeldedaten deines PhoneBlock-Accounts ein</h2>
		
		<p>
			Jetzt benötigst Du die Anmelde-Daten Deines PhoneBlock-Accounts. 
		</p>

		<ul>
			<li>
				<span class="tag is-danger">1</span> 
				gib dem Telefonbuch, das als Sperrliste dienen soll, den Namen <code>Blocklist</code>. Du benötigst diesen 
				Namen nochmals für den nächsten Schritt.
			</li>

			<li>
				Wähle jetzt <span class="tag is-danger">2</span> die Option 
				<i>Telefonbuch eines Online-Anbieters nutzen</i> aus.
			</li>

			<li>
				Dann kannst Du <span class="tag is-danger">3</span>
				den Anbieter <i>CardDAV-Anbieter</i> wählen.
			</li>

			<li>
				Fast geschafft, jetzt kommen die Anmeldedaten <span class="tag is-danger">4</span>. Am besten Du überträgst sie mit Cut&amp;Paste, um Tippfehler zu vermeiden!
				
				<ul>
					<li>
						Trage die URL von des PhoneBlock-Adressbuchs in das Feld <i>Internetadresse des CardDAV-Servers</i> ein:
						<code id="url2">https://phoneblock.net<%=request.getContextPath() %>/contacts/</code><a id="url2_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a>
					</li>

					<li>
						Trage den Benutzernamen <%if (login != null) {%> <code id="login2"><%= JspUtil.quote(login) %></code><a id="login2_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a><%} %>, den Du bei der
						<a href="<%=request.getContextPath() %>/login.jsp<%= LoginServlet.locationParamFirst(SettingsServlet.PATH) %>">Registrierung</a> erhalten hast, in das Feld 
						<i>Benutzername</i> ein. 
					</li>

					<li>
						Das Passwort<%if (token == null) {%>, 
						das Du bei der <a href="<%=request.getContextPath() %>/login.jsp<%= LoginServlet.locationParamFirst(SettingsServlet.PATH) %>">Registrierung</a> erhalten 
						hast, <%} else  {%> <code id="passwd2"><%= JspUtil.quote(token) %></code><a id="passwd2_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></a>,<%}%> muss Du jetzt noch in das Feld
						<i>Passwort</i> eintragen werden.
					</li>
				</ul>
			</li>

			<li>
				Jetzt kannst Du <span class="tag is-danger">5</span> die Anlage des neuen Telefonbuchs bestätigen. Bitte beachte: Wähle <b>keines Deiner Telefone</b> 
				bei dem Punkt "Telefon-Zuordnung" aus! Ansonsten findest Du auf Deinem Telefon nur noch SPAM-Anrufer in den Kontakten.  
			</li>
		</ul>
		
		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="Neues Adressbuch anlegen" src="<%=request.getContextPath() %>/03-add-address-book.png"/>
		  </div>
		</div>

		<h2>Prüfe ob Deine "Blocklist" angelegt wurde</h2>
		
		<p>
			Wenn alles geklappt hat, dann hat Deine FRITZ!Box alle Nummer aus der PhoneBlock-Sperrliste geladen. 
			Du müsstest jetzt ein neues Telefonbuch "Blocklist" in der Rubrik "Telefonie / Telefonbuch" haben. 
			Wenn Du den Reiter "Blocklist" auswählst, kannst Du alle Nummern von aktuellen Spam-Anrufern sehen.
		</p>
		
		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="Abgerufene Blocklist anzeigen" src="<%=request.getContextPath() %>/04-check-blocklist.png"/>
		  </div>
		</div>

		<h2>Lass die FRITZ!Box Anrufe aus der "Blocklist" abweisen</h2>
		
		<p>
			In der Rubrik "Telefonie / Rufbehandlung" kannst Du jetzt das neue Telefonbuch "Blocklist" als zu
			sperrenden Rufnummernbereich festlegen. 
		</p>			
		
		<p>
			Scrolle nach unten bis zu dem Unterpunkt "Rufnummernbereiche sperren" und klicke den Knopf 
			"Bereich hinzufügen".
		</p>

		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="Neue Rufsperre hinzufügen" src="<%=request.getContextPath() %>/05-add-blocklist.png"/>
		  </div>
		</div>
		
		<p>
			In der neuen Maske, wähle die Option "Telefonbuch" in der Auswahl für "Bereich" und selektiere
			das neu angelegte Telefonbuch "Blocklist" als zu sperrendes "Telefonbuch".
		</p>

		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="Anrufer mit Telefonnummer in der Blocklist sperren" src="<%=request.getContextPath() %>/06-create-blocklist.png"/>
		  </div>
		</div>
		
		<p>
			Perfekt! Du hast die Einrichtung erledigt. Dein Telefon sollte jetzt deutlich weniger häufig wegen 
			eines unerwünschten Anrufers klingeln. Und wenn doch, dann kannst Du den 
			Störenfried ganz einfach für Dich und die ganze PhoneBlock-Community zum Schweigen bringen:
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a href="<%=request.getContextPath() %>/block.jsp"><button class="button is-medium is-primary is-fullwidth">Rufnummer sperren</button></a>
		  </div>
		</div>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>