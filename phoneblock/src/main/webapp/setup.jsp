<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Installation</h1>
		
<%
  		Object email = request.getAttribute("email");
  		Object token = request.getAttribute("token");
	  	if (token == null) {
%>
		<p>
			Für die Installation benötigst Du einen <a href="https://avm.de/produkte/fritzbox/">"FRITZ!Box"
				Internetrouter von AVM</a> und einen PhoneBlock-Account. Du hast noch keinen PhoneBlock-Account? Dann erstelle Dir jetzt einen!
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a href="<%=request.getContextPath() %>/signup.jsp">
				<button class="button is-medium is-info is-fullwidth">PhoneBlock-Account erstellen</button>
			</a>
		  </div>
		</div>
<%	  		
	  	} else {
%>
<div class="columns">
  <div class="column">
	<div class="tile is-ancestor">
      <div class="tile is-parent">
      	<article class="tile is-child notification">
	        <p class="title">Deine Anmeldedaten</p>
	        <p class="subtitle">Die folgenden Daten benötigst Du für die Installation.</p>
	        <div class="content">
		
				<div class="field">
				  <label class="label">PhoneBlock-URL</label>
				  <div class="control">
				    <code>https://phoneblock.haumacher.de/phoneblock/contacts/</code>
				  </div>
				</div>
				
				<div class="field">
				  <label class="label">E-Mail</label>
				  <div class="control">
				    <code><%= JspUtil.quote(email) %></code>
				  </div>
				</div>
				
				<div class="field">
				  <label class="label">Passwort</label>
				  <div class="control">
				    <code><%= JspUtil.quote(token) %></code>
				  </div>
				</div>
				
			</div>
		</article>
	  </div>
    </div>
  </div>
</div>
<%	  		
	  	}
%>
		
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
			<img class="image" alt="Anrufliste der Fritz!Box" src="02-navigate-to-addressbooks.png"/>
		  </div>
		</div>
		
		<h2>Trage die Anmeldedaten deines PhoneBlock-Accounts ein</h2>
		
		<p>
			Jetzt benötigst Du die Anmelde-Daten Deines PhoneBlock-Accounts. 
		</p>

		<p>
			<span class="tag is-danger">1</span> 
			gib dem Telefonbuch, das als Sperrliste dienen soll, den Namen <code>Blocklist</code>. Du benötigst diesen 
			Namen nochmals für den nächsten Schritt. Wähle jetzt <span class="tag is-danger">2</span> die Option 
			<i>Telefonbuch eines Online-Anbieters nutzen</i> aus. Dann kannst Du <span class="tag is-danger">3</span>
			den Anbieter <i>CardDAV-Anbieter</i> wählen.
		</p>

		<p>
			Fast geschafft, jetzt kommen die Anmeldedaten <span class="tag is-danger">4</span>! Trage die URL
			von PhoneBlock in das Feld <i>Internetadresse des CardDAV-Servers</i> ein:
		</p>
		
		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<code>https://phoneblock.haumacher.de/phoneblock/contacts/</code>
		  </div>
		</div>
		
		<p>
			Trage Deine E-Mail-Adresse <code><%= JspUtil.quote(email) %></code>, die Du für die 
			PhoneBlock-Registrierung verwendet hast, in das Feld <i>Benutzername</i> ein. Achte auf Groß- und 
			Kleinschreibung: Schreibe die E-Mail-Adresse genauso, wie Du sie in bei der Registrierung geschrieben hast. 
			Am besten überträgst Du sie mit Cut&amp;Paste.
		</p>
		
		<p>
			Das Passwort <code><%= JspUtil.quote(token) %></code>, muss Du jetzt noch in das Feld <i>Passwort</i> 
			in dem Formular in der Fritz!Box eintragen.
		</p>
		
		<p>
			Jetzt kannst Du <span class="tag is-danger">5</span> die Anlage des neuen Telefonbuchs bestätigen.
		</p>
		
		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="Neues Adressbuch anlegen" src="03-add-address-book.svg"/>
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
			<img class="image" alt="Abgerufene Blocklist anzeigen" src="04-check-blocklist.png"/>
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
			<img class="image" alt="Neue Rufsperre hinzufügen" src="05-add-blocklist.png"/>
		  </div>
		</div>
		
		<p>
			In der neuen Maske, wähle die Option "Telefonbuch" in der Auswahl für "Bereich" und selektiere
			das neu angelegte Telefonbuch "Blocklist" als zu sperrendes "Telefonbuch".
		</p>

		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="Anrufer mit Telefonnummer in der Blocklist sperren" src="06-create-blocklist.png"/>
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