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

		<h2>Ich habe keine Fritz!Box, kann ich PhoneBlock trotzdem nutzen?</h2>
		<p>
			Vielleicht, das hängt von deinem Internet-Router/Telefonanlage ab. Deine Telefonanlage (das Ding, das Dir 
			Dein Telefonanbieter zur Verfügung gestellt hat und an der Du Deine Telefone anschließt/anmeldest) muss 
			in der Lage sein, ein Telefonbuch über das <code>CardDAV</code>-Protokoll zu abonieren und alle Anrufe von 
			einer Nummer in einem bestimmten Telefonbuch automatisch abzuweisen (oder an den Anrufbeantworter 
			weiterzuleiten). Ob das bei Deiner Telefonanlage möglich ist, musst Du im Handbuch nachlesen, oder frag 
			Deinen Telefonanbieter.  
		</p>

		<h2>Kann ich PhoneBlock auch auf meinem Mobiltelefon nutzen?</h2>
		<p>
			Nein, bzw. ich habe noch keinen Weg gefunden. Man kann auf Android mit der App 
			<a href="https://play.google.com/store/apps/details?id=com.deependhulla.opensync">Open Sync</a> 
			das PhoneBlock-Adressbuch auf das Mobiltelefon synchronisieren. Dann hat man alle Telefonnummern der
			Blocklist in seinen Kontakten in der Gruppe "SPAM". Allerdings habe ich bisher keine Möglichkeit gefunden,
			alle Anrufe von allen Kontakten einer Gruppe zu blockieren. Die App, die diese Funktionalität verspricht 
			(<a href="http://android.insadco.com/group_blocker">Group Blocker</a>) scheint es im PlayStore nicht mehr 
			zu geben. Außerdem will man eigentlich auch nicht alle Telefonterroristen in seinen Kontakten haben. 
			Hier müsst man wohl eine eigene App schreiben, welche das Abweisen von Anrufen implementiert, ohne die 
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