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
			Ja, aktuell ist PhoneBlock kostenlos. Der Server ist ein Raspberry PI 4 auf meinem Schreibtisch 
			und verursacht keine zusätzlichen Kosten. Aufgrund der aktuellen Nutzerzahlen und der leider nur sehr moderaten 
			Steigerungsrate gehe ich aktuell davon aus, dass der Raspberry PI auf unabsehbare Zeit genug Power hat, 
			um das Projekt zu betreiben. Erst wenn ein professionelles Hosting notwendig wird, könnte dies in Zukunft 
			einen gewissen Unkostenbeitrag von den PhoneBlock-Nutzern erfordern.
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

		<h2>Kann ich PhoneBlock auch auf meinem Mobiltelefon nutzen?</h2>
		<p>
			Ja, du kannst das PhoneBlock-Adressbuch abonieren und siehst dann gleich, ob es sich lohnt, einen Anruf
			anzunehmen oder bei einem verpassten Anruf zurückzurufen. Spam-Anrufe direkt abweisen geht aktuell nur 
			im Festnetz mit einer Fritz!Box. Für Android gibt es eine 
			<a href="<%= request.getContextPath()%>/setup-android/">ausführliche Installationsanleitung</a>. 
		</p>
		
		<p> 
			Mit dem IPhone <a href="<%=request.getContextPath()%>/link/carddav-install">geht das sogar 
			ganz ohne Zusatzinstallation</a> - ich habe es allerdings nicht ausprobiert. Wenn Du das testest und
			es nicht funktionieren sollte, dann melde Dich bitte (siehe unten).
		</p>
		
		<p>
			Allerdings habe ich bisher keine Möglichkeit gefunden,
			alle Anrufe von allen Kontakten einer Gruppe (z.B. "SPAM") zu blockieren. Die App, die diese Funktionalität verspricht 
			(<a target="_blank" href="<%=request.getContextPath()%>/link/group-blocker">Group Blocker</a>) scheint es im PlayStore nicht mehr 
			zu geben. Außerdem will man eigentlich auch nicht alle Telefonterroristen in seinen Kontakten haben. 
			Hier müsste man wohl eine eigene App schreiben, welche das Abweisen von Anrufen implementiert, ohne die 
			Nummern zu den Kontakten hinzuzufügen. 
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

		<h2>Ich finde PhoneBlock toll, kann ich eine Spende machen?</h2>
		<p>
			PhoneBlock verfolgt aktuell<sup>(*)</sup> keinerlei kommerzielle Interessen. Spenden sind aktuell keine
			notwendig, da keine Kosten entstehen und ich nicht das Finanzamt auf den Plan rufen möchte. Wenn Du 
			PhoneBlock gut findest, dann kannst Du aber trotzdem etwas tun: Poste Links zur PhoneBlock-Seite in 
			Foren, auf FaceBook, in Kommentaren zu Zeitschriftenartikeln, in Deinem Blog oder wo Du das
			sonst für angebracht hälst. Nur wenn die Verbreitung von PhoneBlock schneller wächst, hat das Projekt einen
			Effekt gegen den unerhörten Telefonterror, der im deutschen Telefonnetz tobt.
		</p>
		<p>
			(*) Aufgrund der aktuellen Nutzerzahlen und der leider äußerst moderaten Steigerungsrate gehen ich davon aus, 
			dass mein RasperryPI auf unabsehbare Zeit genug Rechenleistung bietet, um PhoneBlock kostenneutral zu 
			betreiben. Ab 100.000 Nutzern wäre es natürlich verlockend, wenn jeder 0.50 Ct pro Jahr bezahlen würde :-)
		</p>

		<h2>Ich habe einen Wunsch, was PhoneBlock unbedingt können sollte, wo kann ich den äußern?</h2>
		<p>
			Wenn Du einen GitHub-Account hast, kannst Du auf der <a target="_blank" href="<%=request.getContextPath()%>/link/issues" target="_blank">PhoneBlock Projekt-Seite</a> 
			ein Ticket öffnen und den Wunsch beschreiben. Gerne kannst Du den Vorschlag aber auch mit mir per E-Mail 
			diskutieren: <code>Bernhard Haumacher &lt;<button onclick="return showaddr(this);">...</button>&gt;</code>  
		</p>

		<h2>Meine Frage wird hier nicht beantwortet, wer hilft?</h2>
		<p>
			Schreib doch einen Kommentar auf auf der <a target="_blank" href="<%=request.getContextPath()%>/link/facebook" target="_blank">Facebook-Seite von PhoneBlock</a>, 
			dann können alle die Frage lesen. 
			Gerne kannst Du mir auch eine persönliche Nachricht per E-Mail zukommen lassen: 
			<code>Bernhard Haumacher &lt;<button onclick="return showaddr(this);">...</button>&gt;</code>  
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