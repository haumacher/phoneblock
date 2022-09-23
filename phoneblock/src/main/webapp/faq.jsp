<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8"%>
<html>
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
			und verursacht keine zusätzlichen Kosten. Ab einer bestimmten Nutzerzahl wird diese Lösung aber sicher zu
			einem Engpass. Ich bin gespannt, ab wann diese Grenze erreicht ist. Ein professionelles Hosting ist 
			aber nicht zum Nulltarif zu haben und könnte in Zukunft einen gewissen Unkostenbeitrag von den 
			PhoneBlock-Nutzern erfordern.
		</p>

		<h2>Was ist der Unterschied zwischen PhoneBlock und dem "<a target="_blank" href="https://amzn.eu/d/hc6WGbz">tellows Anrufschutz</a>"?</h2>
		<p>
			Beide Ansätze funktionieren ähnlich über ein Internet-Telefonbuch, das man in seiner Fritz!Box einrichtet 
			und die Nummern in diesem Telefonbuch blockiert. PhoneBlock bietet Dir darüber hinaus aber die Möglichkeit
			Deine Blocklist zu personalisieren und ist zudem auch noch kostenlos (siehe oben).
		</p>
		<p> 
			In Deine personalisierte Blocklist kannst Du selbst Nummern hinzufügen und, was 
			noch wichtiger ist, auch Nummern aus dem Telefonbuch löschen (siehe auch "Eine Nummer wird 
			fälschlicherweise blockiert, was tun?" weiter unten). Interessant ist das, wenn PhoneBlock eine 
			Nummer auf die Blocklist aufgenommen hat, von der Du aber angerufen werden möchtest. Z.B. kann ganz leicht
			eine Nummer z.B. von einem Internet-Anbieter auf die Blocklist geraten, wenn sich viele Leute von 
			Rückrufen von dieser Nummer genervt fühlen, Du aber auf einen Rückruf wartest. Das wird auch ganz schön in 
			diesem <a target="_blank" href="https://www.youtube.com/watch?v=hNlRczRivGo">YouTube-Video</a> erklärt. Mit PhoneBlock 
			kannst Du Deine Blocklist anpassen, Nummern, die Du selbst darauf gesetzt hast, bleiben gesperrt und 
			Nummern, die Du von der Liste gelöscht hast, werden sicher nie wieder zu Deiner persönlichen Liste 
			hinzugefügt. 
		</p>

		<h2>Ich habe keine Fritz!Box, kann ich PhoneBlock trotzdem nutzen?</h2>
		<p>
			Vielleicht, das hängt von deinem Internet-Router/Telefonanlage ab. Deine Telefonanlage (das Ding, das Dir 
			Dein Telefonanbieter zur Verfügung gestellt hat und an der Du Deine Telefone anschließt/anmeldest) muss 
			in der Lage sein, ein Telefonbuch über das <code>CardDAV</code>-Protokoll zu abonieren und alle Anrufe von 
			einer Nummer in einem bestimmten Telefonbuch automatisch abzuweisen (oder an den Anrufbeantworter 
			weiterzuleiten). Ob das bei Deiner Telefonanlage möglich ist, musst Du im Handbuch nachlesen, oder frag 
			Deinen Telefonanbieter. Solltest Du die Blocklist erfolgreich in einen anderen Router installiert haben, 
			sag bitte Bescheid, damit die Installationsanleitung angepasst werden kann.
		</p>

		<h2>Kann ich PhoneBlock auch auf meinem Mobiltelefon nutzen?</h2>
		<p>
			Nicht zum Blockieren, sondern nur für die Anzeige, dass ein Anrufer potentiell unerwünscht ist. Auf Android kann man 
			mit der App <a target="_blank" href="https://play.google.com/store/apps/details?id=com.deependhulla.opensync">Open Sync</a> 
			das PhoneBlock-Adressbuch auf das Mobiltelefon synchronisieren. Dann hat man alle Telefonnummern der
			Blocklist in seinen Kontakten in der Gruppe "SPAM". Ein Anruf von einer Nummer aus der Blocklist wird dann 
			beispielsweise als "SPAM: 03016637169" angezeigt, und man weiß, dass man das wegdrücken kann.
		</p>
		
		<p> 
			Mit dem IPhone <a href="https://www.reiermann.de/hilfe/carddav-auf-ipad-oder-iphone-einrichten/470/">geht das sogar 
			ganz ohne Zusatzinstallation</a> - ich habe es allerdings nicht ausprobiert. Wenn Du das testest und
			es nicht funktionieren sollte, dann melde Dich bitte (siehe unten).
		</p>
		
		<p>
			Allerdings habe ich bisher keine Möglichkeit gefunden,
			alle Anrufe von allen Kontakten einer Gruppe (z.B. "SPAM") zu blockieren. Die App, die diese Funktionalität verspricht 
			(<a target="_blank" href="http://android.insadco.com/group_blocker">Group Blocker</a>) scheint es im PlayStore nicht mehr 
			zu geben. Außerdem will man eigentlich auch nicht alle Telefonterroristen in seinen Kontakten haben. 
			Hier müsste man wohl eine eigene App schreiben, welche das Abweisen von Anrufen implementiert, ohne die 
			Nummern zu den Kontakten hinzuzufügen. 
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

		<h2>Eine Nummer wird fälschlicherweise blockiert, was tun?</h2>
		<p>
			Kein Problem, öffne das Telefonbuch "Blocklist" in Deiner Fritz!Box, suche die Nummer und lösche sie aus
			der Blocklist. Damit wird diese Nummer aus Deiner persönlichen Blocklist entfernt und wird auch nie wieder
			darin aufgenommen, auch wenn sich ganz viele andere von Anrufen dieser Nummer genervt fühlen und sie 
			blockieren. Mit dem Entfernen einer Nummer aus Deiner Blocklist erzeugst Du für diese Nummer eine 
			Ausnahmeregel, die verhindert, dass diese Nummer je wieder in Deine Blocklist aufgenommen wird. 
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

		<h2>Ich habe einen Wunsch, was PhoneBlock unbedingt können sollte, wo kann ich den äußern?</h2>
		<p>
			Wenn Du einen GitHub-Account hast, kannst Du auf der <a target="_blank" href="https://github.com/haumacher/phoneblock/issues" target="_blank">PhoneBlock Projekt-Seite</a> 
			ein Ticket öffnen und den Wunsch beschreiben. Gerne kannst Du den Vorschlag aber auch mit mir per E-Mail 
			diskutieren: <code>Bernhard Haumacher &lt;<button onclick="return showaddr(this);">...</button>&gt;</code>  
		</p>

		<h2>Meine Frage wird hier nicht beantwortet, wer hilft?</h2>
		<p>
			Schreib doch einen Kommentar auf auf der <a target="_blank" href="https://www.facebook.com/PhoneBlock" target="_blank">Facebook-Seite von PhoneBlock</a>, 
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