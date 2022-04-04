<!DOCTYPE html>
<%@page pageEncoding="UTF-8" %>
<html>
<head>
	<title>PhoneBlock: Der Spam-Filter für Dein Telefon</title>
	
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
	<script type="text/javascript" src="phoneblock.js"></script>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Rufnummer sperren</h1>
	
		<p>
			Es ist doch noch eine Nervensäge durch das PhoneBlock-Netz geschlüpft? Ärgerlich aber kein Problem, 
			gleich bist Du ihn los. Hierfür fügst Du die Nummer des Störenfrieds einfach der "Blocklist" hinzu. 
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a href="http://fritz.box" target="_blank"><button class="button is-medium is-primary is-fullwidth">Melde Dich dafür an Deiner FRITZ!Box an</button></a>
		  </div>
		</div>
		
		<h2>Zeige die Anruferliste an</h2>
		
		<p>
			In der Benutzeroberfläche Deiner FRITZ!Box kannst Du alle Anrufer des letzten Zeit sehen. Wenn alles 
			gut geglaufen ist, sieht die Liste aus wie unten gezeigt. Ein unerwünschter Anruf wurde erkannt, als
			Spam markiert und das Gespräch wurde abgewiesen (der Anruf wir mit einem roten X dargestellt). 
		</p>
		
		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<img class="image" alt="navigate-to-calls" src="07-spam-caller.png"/>
		  </div>
		</div>
		
		<p>
			In diesem Fall ist nichts zu tun. Der unerwünschte Anrufer befindet sich schon auf der "Blocklist" und 
			kann Dich nicht mehr stören. 
		</p>
		
		<h2>Neuer "Blocklist"-Eintrag</h2>
		
		<p>
			Hat die Nervensäge eine neue Telefonnummer oder hat ein neuer Telefonterrorist sein Business aufgenommen, 
			kann Dich immer noch ein unerwünschter Anruf erreichen. In diesem Fall kannst Du hier einen neuen Eintrag
			zur "Blocklist" hinzufügen. 
		</p>
		
		<div class="columns">
		  <div class="column is-8 is-offset-2">
			<div class="tile is-ancestor">
		      <div class="tile is-parent">
		      	<article class="tile is-child notification is-danger">
			        <p class="title">Achtung!</p>
			        <p class="subtitle">Keine persönlichen Feinde eintragen!</p>
			        <div class="content">
			          <p>
			          Ein Eintrag in der "BlockList" sperrt den Anrufer nicht nur für Dich, sondern 
			          für alle Mitglieder der PhoneBlock-Community! Wenn Du also keine Anrufe von Deiner
			          Schwiegermutter mehr erhalten möchtest, dann trage bitte ihre Nummer in die lokale Liste
			          mit Rufsperren in Deiner FRITZ!Box ein, aber nicht in die PhoneBlock-"Blocklist"!
			          </p>
			          
			          <p>
			          Bei Verstößen wird Dein Account ohne Vorwarnung und unwiderruflich gesperrt!
			          </p>
			        </div>
			  	</article>
			  </div>
			</div>
	    </div>
	 </div>
	 
	 <p>
	 	Du bist also sicher, dass dich ein Werbeanruf erreicht hat, den Du nicht bestellt hast und von dem Du 
	 	sicher bist, dass auch niemand anderes ihn erhalten will? Dann los - sperr die Nummer...
	 </p>
		
	<p>
		Eine Nummer wird gesperrt, indem Du einen neuen Telefonbucheintrag in dem Telefonbuch mit Namen "Blocklist" 
		machst. Deine FRITZ!Box lädt diesen neuen Eintrag auf den PhoneBlock-Server und alle anderen angeschlossenen
		Mitglieder der PhoneBlock-Community erhalten diesen Eintrag ebenfalls in ihrer Blocklist. Um einen neuen
		Telefonbucheintrag zu machen, klicke auf das Buch-Symbol am Ende der Zeile mit der Nummer des unerwünschten
		Anrufs.
	</p>
	
	<p>
		Wähle jetzt das Telefonbuch "Blocklist" aus:
	</p>
	
	<div class="columns">
	  <div class="column is-8 is-offset-2">
		<img class="image" alt="navigate-to-calls" src="08-mark-as-spam.png"/>
	  </div>
	</div>
	
	<p>
		Klicke "Weiter" und wähle "neu anlegen":
	</p>
	
	<div class="columns">
	  <div class="column is-8 is-offset-2">
		<img class="image" alt="navigate-to-calls" src="09-create-entry-create.png"/>
	  </div>
	</div>
	
	<p>
		Auf der Folgemaske gib noch einen beliebigen Namen ein und klicke "OK". Der Name wird nach der Synchronisation 
		mit dem PhoneBlock-Server ersetzt durch "SPAM: Rufnummer", du musst Dir als keine Mühe geben, hier etwas 
		hübsches einzugeben:
	</p>

	<div class="columns">
	  <div class="column is-8 is-offset-2">
		<img class="image" alt="navigate-to-calls" src="10-add-entry-save.png"/>
	  </div>
	</div>
	
	<p>
		Fertig! Du hast Dich und die ganze PhoneBlock-Community von einer weiteren Nervensäge erlöst, danke!		
	</p>
</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>