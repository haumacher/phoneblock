<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<html>
<%
	request.setAttribute("title", "Fragen und Antworten zu PhoneBlock");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Fragen und Antworten zu PhoneBlock</h1>

		<h2>Ist PhoneBlock kostenlos?</h2>
		<p>
			Ja, aktuell musst Du für die Nutzung von PhoneBlock nichts bezahlen. Allerdings verursacht der Betrieb von PhoneBlock
			mittlerweile laufende Kosten für Servermiete, Internetzugang, Domainregistrierung. Um diese zu decken, wäre es schön, 
			wenn Du <b><a href="<%=request.getContextPath()%>/support.jsp">eine kleine Spende leisten</a></b> könntest.
		</p>
		
		<p>
			Lange Zeit lief PhoneBlock auf einem <a href="https://www.raspberrypi.com/">Raspberry PI 4</a> auf 
			meinem Schreibtisch und verursachte daher kaum zusätzlichen Kosten (abgesehen von meinem Internetanschluss und 
			der Domain-Registrierung). Aufgrund von steigenden Nutzerzahlen und vor allem um eine bessere Verfügbarkeit des 
			Dienste zu gewährleisten, ist PhoneBlock aber mittlerweile in die <a href="https://www.hetzner.com">Hetzner-Cloud</a> 
			(Serverstandort Falkenstein, Deutschland) umgezogen. 
		</p>

		<h2>Was ist der Unterschied zwischen PhoneBlock und dem "<a target="_blank" href="<%=request.getContextPath()%>/link/tellows">tellows Anrufschutz</a>"?</h2>
		<p>
			Der <a href="<%=request.getContextPath()%>/anrufbeantworter/">PhoneBlock-Anrufbeantworter</a> kennt keine 
			Größenbeschränkung des Sperrlisten-Telefonbuchs. Er nimmt das Gespräch für alle Nummern, die auf der 
			Sperrliste stehen, an. Es ist keine nächtliche Aktualisierung der Blockliste in Deiner Fritz!Box notwendig, 
			Die Blockliste ist immer aktuell.
		</p>
		
		<p>  
			Den <a href="<%=request.getContextPath()%>/anrufbeantworter/">PhoneBlock-Anrufbeantworter</a> kannst Du in jeden
			VOIP-fähigen Internetrouter installieren, auch dann wenn das CardDAV-Protokoll für Internet-Telefonbücher von Deinem 
			Router nicht unterstützt wird. 
		</p>
		
		<p>
			Bei beiden Angeboten kannst Du die <a href="<%=request.getContextPath()%>/setup.jsp">Blockliste als Internet-Telefonbuch</a>, 
			in Deiner Fritz!Box einrichten 
			und die Nummern in diesem Telefonbuch sperren. PhoneBlock bietet Dir darüber hinaus aber die Möglichkeit
			Deine Blocklist zu personalisieren und ist zudem auch noch kostenlos (siehe oben).
		</p>
		
		<p> 
			In Deine personalisierte Blocklist kannst Du selbst Nummern hinzufügen und, was 
			noch wichtiger ist, auch Nummern von der Sperrung ausnehmen (siehe auch "Eine Nummer wird 
			fälschlicherweise blockiert, was tun?" weiter unten). Interessant ist das, wenn PhoneBlock eine 
			Nummer auf die Blocklist aufgenommen hat, von der Du aber angerufen werden möchtest. Z.B. kann ganz leicht
			eine Nummer z.B. von einem Internet-Anbieter auf die Blocklist geraten, wenn sich viele Leute von 
			Rückrufen von dieser Nummer genervt fühlen, Du aber auf einen Rückruf wartest. Das wird auch ganz schön in 
			diesem <a target="_blank" href="<%=request.getContextPath()%>/link/tellows-problems">YouTube-Video</a> erklärt. 
			Mit PhoneBlock kannst Du Deine Blocklist anpassen, Nummern, die Du selbst darauf gesetzt hast, bleiben 
			gesperrt und Nummern, die Du zu Deiner Whitelist hinzugefügt hast, werden sicher bei Dir nicht blockiert.
		</p>

		<h2>Meine Fritz!Box unterstützt noch kein CardDAV, kann ich PhoneBlock trotzdem nutzen?</h2>
		<p>
			Ja, es gibt zwei Möglichkeiten: 
		</p>
		<ol>
		<li>Nutze statt des Telefonbuchs den PhoneBlock-Anrufbeantworter (aktuell noch im Beta-Test). Den 
			PhoneBlock-Anrufbeantworter meldest Du wie ein Internettelefon an Deine Fritz!Box an und er geht immer ran,
			wenn eine SPAM-Nummer anruft, so schnell, dass dein Telefon nicht oder nur ganz kurz klingelt.
			Aktuell ist der PhoneBlock-Anrufbeantworter erst auf einem Test-System verfügbar, das eine separate Anmeldung
			erfordert: <a href="<%=request.getContextPath()%>/anrufbeantworter/">Zum Anrufbeantworter-Testsystem</a>. Wenn Du 
			Den Anrufbeantworter erfolgreich installieren konntest - oder Probleme dabei hattest, gibt bitte eine Rückmeldung,
			damit das neue Werkzeug möglichst bald allgemein verfügbar wird.
		</li>
		<li>Nutze das Tool <a href="https://spamblockup.jimdofree.com/">SpamBlockUp</a>, um die Blockliste in Deine 
			Fritz!Box zu laden. Hierfür benötigst Du allerdings einen PC, auf dem Du das Programm installieren kannst.
			SpamBlockUp kann auch die PhoneBlock-Blockliste verwenden und von außen in Deine Fritz!Box laden. 
			Für Details musst Du Dich aber an den Author dieses Programms wenden. PhoneBlock steht zu SpamBlockUp in 
			keiner Beziehung. 
		</li>
		</ol>

		<h2>Ich habe keine Fritz!Box, kann ich PhoneBlock trotzdem nutzen?</h2>
		
		<p>  
			Den <a href="<%=request.getContextPath()%>/anrufbeantworter/">PhoneBlock-Anrufbeantworter</a> kannst Du in jeden
			VOIP-fähigen Internetrouter installieren, auch dann wenn das CardDAV-Protokoll für Internet-Telefonbücher von Deinem 
			Router nicht unterstützt wird. Installiere den Anrufbeantworter als normales VOIP-Telefon, das sich über 
			Internet an Deiner Box anmeldet.
		</p>
		
		<p>
			Um die Blockliste als Telefonbuch zu importieren, muss Dein Internet-Router/Telefonanlage in der Lage sein, 
			ein Telefonbuch über das <code>CardDAV</code>-Protokoll zu abonieren und alle Anrufe von 
			einer Nummer in einem bestimmten Telefonbuch automatisch abzuweisen (oder an den Anrufbeantworter 
			weiterzuleiten). Ob das bei Deiner Telefonanlage möglich ist, musst Du im Handbuch nachlesen, oder frag 
			Deinen Telefonanbieter. Solltest Du die Blocklist erfolgreich in einen anderen Router installiert haben, 
			sag bitte Bescheid, damit die Installationsanleitung angepasst werden kann.
		</p>

		<h2>Kann ich PhoneBlock auf einen Android Mobiltelefon nutzen?</h2>
		<p>
			Ja, du kannst das PhoneBlock-Adressbuch abonieren und siehst dann gleich, ob es sich lohnt, einen Anruf
			anzunehmen oder bei einem verpassten Anruf zurückzurufen. Spam-Anrufe direkt abweisen geht aktuell nur 
			im Festnetz mit einer Fritz!Box. Für Android gibt es eine 
			<a href="<%= request.getContextPath()%>/setup-android/">ausführliche Installationsanleitung</a>. 
		</p>
		
		<h2>Kann ich PhoneBlock auf einen iPhone nutzen?</h2>
		<p> 
			Ja, mit dem iPhone geht das sogar ganz ohne Installation zusätzlicher Apps. Schau Dir hier die 
			<a href="<%= request.getContextPath()%>/setup-iphone/">Installationsanleitung</a> an.
		</p>

		<h2 id="Error26">Meine FritzBox! meldet "Fehler 26" bei der Einrichtung</h2>
		
		<p>
			Es ist unklar, wo dieser Fehler herkommt und was er bedeutet. Nutzer berichten allerdings dass die 
			folgenden Schritte helfen, um PhoneBlock trotzdem einrichten zu können:
		</p>
		
		<ol>
			<li>Entferne das Telefonbuch nocheinmal vollständig</li>
			<li>Führe einen Reboot der Box durch und prüfe, ob das Telefonbuch danach wirklich entfernt ist. 
				Wenn nicht wiederhole die Entfernung.</li>
			<li>Erstelle das Telefonbuch neu. Stelle dabei sicher, dass Du wirklich die Adresse des Servers richtig 
				kopiert hast. Stelle sicher, dass sich keine Leerzeichen um Anfang oder am Ende von Nutzernamen 
				und Passwort befinden.</li>
		</ol>
		
		<p>
			Bitte beachte: Wenn Du das Passwort für Deinen PhoneBlock-Zugang änderst, dann reicht es nicht, in den 
			Telefonbucheinstellungen das neue Passwort zu setzen. Die Fritz!Box scheint hier ein Problem bei der 
			Aktualisierung von Zugangsdaten zu haben. Lösche stattdessen das Telefonbuch und richte es erneut ein.
		</p>

		<h2 id="disposable-mail">Warum kann ich mich nicht mit einer Wegwerf-E-Mail-Adresse registrieren?</h2>
		<p>
			Wenn Du PhoneBlock nutzt, dann verbrauchst Du kontinuierlich Rechenkapazität und Internetbandbreite, da 
			die Blockliste täglich aktualisiert wird. Der Betrieb von PhoneBlock verbraucht aktuell ca. 100GB 
			Datenvolumen pro Monat. Aktuell ist das für Dich kostenlos. Falls beim Betrieb aber irgendetwas nicht 
			"rund" läuft, erwarte ich, dass Du ansprechbar bist, um etwaige Probleme abzustellen. Wenn Du Dich
			mit einer Wegwerf-Adresse registrierst, ist das nicht möglich. Eine Registrierung mit einer Wegwerf-Adressse
			wäre genauso gut wie überhaupt keine Registrierung. 
		</p>

		<h2>Ab wann wird eine Nummer von PhoneBlock blockiert?</h2>
		<p>
			Wenn Du selbst eine Nummer zur Blocklist hinzufügst, werden Anrufe von dieser Nummer für Dich sofort 
			blockiert. Andere Abonenten der PhoneBlock-Blocklist erhalten diese Nummer aber nicht sofort in ihrer 
			Blocklist. Erst, wenn mindestens drei Abonenten die Nummer bei sich ebenfalls blockiert haben, wird die 
			neue Nummer an alle Abonenten verteilt. Die genauen Regeln, ab wann eine Nummer auf die Blocklist kommt
			und wann sie wieder aus ihr entfernt wird, werden aber noch angepasst. Sicher ist aber, eine Nummer die
			Du selbst zur Blocklist hinzugefügt hast, bleibt für Dich auf alle Fälle gesperrt.
		</p>
		
		<h2>Ich erhalte trotz PhoneBlock immer noch Spam-Anrufe, was tun?</h2>
		<p>
			Es gibt ständig neue Spam-Nummern, daher kann PhoneBlock keinen 100%igen Schutz bieten. 
			<a href="<%=request.getContextPath()%>/block.jsp">Trage  den
			neuen unerwünschten Anrufer in die Blocklist ein</a> und hilf damit auch anderen, den Quälgeist 
			schnell los zu werden!  
		</p>

		<h2>Hilfe, ich habe eine Nummer aus Versehen zur Blockliste hinzugefügt</h2>
		<p>
			Wenn Du in Deiner Fritz!Box einen neuen "Kontakt" in dem Blocklist-Telefonbuch erstellst, teilt Deine 
			Fritz!Box das dem PhoneBlock-Server mit und der interpretiert das als Beschwerde über diese Nummer. 
			Darüberhinaus landet die Nummer dann in deiner persönlichen Sperrliste, damit Du sicher nicht mehr von 
			dieser Nummer genervt wirst. Aufgrund der komprimierten Speicherung von vielen Nummern in einem Kontakt, 
			kann man leider auf dieselbe Art und Weise keine Nummer aus der persönlichen Sperrliste löschen. 
			Stattdessen musst Du Dich in so einem Fall hier auf der Seite mit Deinen Zugangsdaten anmelden und die 
			Nummer in <a href="<%= request.getContextPath() + SettingsServlet.PATH %>#blacklist">Deinen Einstellungen</a> 
			aus der  "Black-List" löschen. Beim nächsten Synchronisationslauf sollte die Nummer dann auch wieder aus
			der Blockliste Deiner Fritz!Box verschwinden. 
		</p>

		<h2>Eine Nummer wird fälschlicherweise blockiert, was tun?</h2>
		<p>
			Kein Problem, öffne <a href="<%= request.getContextPath() + SettingsServlet.PATH %>#whitelist">Deine Einstellungen</a> 
			und füge die Nummer zu Deiner White-List hinzu. Deine White-List ist eine Liste von Telefonnummern, die Du 
			von der Sperrung ausgenommen hast. Sie landen nie auf Deiner Blocklist, auch wenn sich ganz viele andere 
			von Anrufen dieser Nummer genervt fühlen und sie blockieren. 
		</p>

		<h2>Hilfe, ich habe eine Nummer aus Versehen zur Blockliste hinzugefügt!</h2>
		<p>
			Wenn Du in Deiner Fritz!Box einen neuen "Kontakt" in dem Blocklist-Telefonbuch erstellst, teilt Deine 
			Fritz!Box das dem PhoneBlock-Server mit und der interpretiert das als Beschwerde über diese Nummer. 
			Darüberhinaus landet die Nummer dann in deiner persönlichen Sperrliste, damit Du sicher nicht mehr von 
			dieser Nummer genervt wirst. Aufgrund der komprimierten Speicherung von vielen Nummern in einem Kontakt, 
			kann man leider auf dieselbe Art und Weise keine Nummer aus der persönlichen Sperrliste löschen. 
			Stattdessen musst Du Dich in so einem Fall hier auf der Seite mit Deinen Zugangsdaten anmelden und die 
			Nummer in <a href="<%= request.getContextPath() + SettingsServlet.PATH %>#blacklist">Deinen Einstellungen</a> 
			aus der  "Black-List" löschen. Beim nächsten Synchronisationslauf sollte die Nummer dann auch wieder aus
			der Blockliste Deiner Fritz!Box verschwinden. 
		</p>

		<h2>Ich habe Angst, dass PhoneBlock Nummern blockiert, die ich benötige, was tun?</h2>
		<p>
			Mit PhoneBlock musst du Nummern nicht unbedingt blockieren. Du hast die folgenden Möglichkeiten:
		</p>
		<ol>
			<li>Statt einer Blockierung richte eine Weiterleitung auf Deinen Anrufbeantworter ein. Wenn jemand anruft, 
			der wirklich etwas von Dir will, dann wird er eine Nachricht hinterlassen und Du kannst zurückrufen.</li>
			<li>Lass den Installationsschritt mit der Blockierung ganz weg. Richte nur das PhoneBlock-Telefonbuch in 
			Deiner Fritz!Box ein. Dann wird Dir beim Anruf im Display des Telefons z.B. "SPAM: 03016637169" angezeigt. 
			Du kannst dann entscheiden, ob Du ran gehst, weil Du gerade auf einen Rückruf wartest, oder ob Du den 
			Anrufer wegdrückst.</li>
		</ol>

		<h2>Muss ich das Telefonbuch manuell aktualisieren?</h2>
		<p>
			Nein, das passiert ganz automatisch jede Nacht. Deine Fritz!Box kontaktiert jede Nacht den PhoneBlock-Server
			und gleicht die Nummern der Blockliste mit dem Blocklist-Telefonbuch ab. Daher bleibt Deine Blockliste immer
			aktuell. Wenn über einen längeren Zeitraum (aus welchen Gründen auch immer) kein Abruf der Blockliste mehr von
			Deiner Fritz!Box erfolgt, schreibt Dir PhoneBlock eine E-Mail, damit Du die Konfiguration überprüfen kannst, 
			bevor Deine Blockliste veraltet.
		</p>

		<h2>Wann genau erfolgt die Aktualisierung der Blockliste?</h2>
		<p>
			Das entscheidet Deine Fritz!Box. Wenn Du nicht explizit auf den "aktualisieren" Knopf des Blocklist-Telefonbuchs
			drückst, dann würfelt Deine Fritz!Box einen Zeitpunkt zwischen 0 und 6 Uhr für die Aktualisierung. Das ist 
			von AVM ganz schlau gemacht, weil sich damit die Abrufe relativ gleichmäßig über die ganze Nacht verteilen und so
			die Server-Last begrenzt bleibt.
		</p>
		
		<h2>Ich finde PhoneBlock toll, kann ich eine Spende machen?</h2>
		<p>
			Ja, wenn Dir PhoneBlock gefällt, dann kannst Du Dich gerne mit einer <a href="<%=request.getContextPath()%>/support.jsp">kleinen Spende</a> 
			an den laufenden Kosten für Servermiete, Domain-Registrierung, Internetzugang usw. beteiligen.
		</p>
		
		<p>
			Wichtig ist aber auch, dass PhoneBlock bekannter wird. Wenn Du also nichts spenden kannst oder willst, 
			dann poste doch Links zur PhoneBlock-Seite in Foren, auf FaceBook, in Kommentaren zu Zeitschriftenartikeln, 
			in Deinem Blog oder wo Du das sonst für angebracht hälst. Nur wenn die Verbreitung von PhoneBlock schneller wächst, 
			hat das Projekt einen Effekt gegen den unerhörten Telefonterror, der im deutschen Telefonnetz tobt.
		</p>

		<h2>Ich habe einen Wunsch, was PhoneBlock unbedingt können sollte, wo kann ich den äußern?</h2>
		<p>
			Wenn Du einen GitHub-Account hast, kannst Du auf der <a target="_blank" href="<%=request.getContextPath()%>/link/issues">PhoneBlock Projekt-Seite</a> 
			ein Ticket öffnen und den Wunsch beschreiben. Gerne kannst Du den Vorschlag aber auch mit mir per E-Mail 
			diskutieren: <code>Bernhard Haumacher &lt;<button class="showaddr">...</button>&gt;</code>
		</p>
		
		<h2>Wie kann ich PhoneBlock wieder deinstallieren?</h2>
		<p>
			Du benötigst PhoneBlock nicht mehr, oder bist nicht damit zufrieden? Schade, vielleicht kannst Du ja 
			<a target="_blank" href="<%=request.getContextPath()%>/link/issues">einen Verbesserungsvorschlag machen</a>?
		</p>
		
		<p>
			Zum Deinstallieren gehst Du genau wie bei der Installation nur rückwärts vor. Je nachdem wie Du PhoneBlock installiert hast 
			(als <a href="<%=request.getContextPath()%>/setup.jsp">Telefonbuch</a> oder als 
			<a href="<%=request.getContextPath()%>/anrufbeantworter/">Anrufbeantworter</a>, oder beides), must Du die entsprechenden 
			Einstellungen wieder löschen. Beim Telefonbuch: Die Rufsperre (oder die Rufumleitung) löschen, welche sich auf alle Anrufe 
			aus dem PhoneBlock-Telefonbuch bezieht. Anschließend kannst Du das PhoneBlock-Telefonbuch löschen. Beim Anrufbeantworter: 
			Das Telefoniegerät "PhoneBlock" löschen und anschließend die DynIP-Freigabe deaktivieren (wenn Du nicht einen separaten 
			DynIP-Dienst verwendet hast). Anschließend solltest Du noch Deinen PhoneBlock-Account in den 
			<a href="<%= request.getContextPath() + SettingsServlet.PATH %>">Einstellungen</a> löschen.
		</p>

		<h2>Meine Frage wird hier nicht beantwortet, wer hilft?</h2>
		<p>
			Schreib doch einen Kommentar auf auf der <a target="_blank" href="<%=request.getContextPath()%>/link/facebook">Facebook-Seite von PhoneBlock</a>, 
			dann können alle die Frage lesen. 
			Gerne kannst Du mir auch eine persönliche Nachricht per E-Mail zukommen lassen: 
			<code>Bernhard Haumacher &lt;<button class="showaddr">...</button>&gt;</code>
		</p>

<!-- 
		<h2></h2>
		<p>
		
		</p>
 -->
	</div>
	
	<div class="tile is-ancestor">
		<div class="tile is-parent is-6">
			<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/signup.jsp">
				<p class="title">Klar zum Loslegen?</p>
				<p class="subtitle">PhoneBlock-Account erstellen!</p>
			</a>
		</div>

		<div class="tile is-parent is-6 ">
			<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/setup.jsp">
				<p class="title">Wie einrichten?</p>
				<p class="subtitle">Check die Installationsanleitung!</p>
			</a>
		</div>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>
