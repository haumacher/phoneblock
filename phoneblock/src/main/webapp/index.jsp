<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<html>
<head>
<link rel="canonical" href="https://phoneblock.haumacher.de/phoneblock/" />
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<div style="float: right; max-width: 15%;">
			<img alt="PhoneBlock Logo" src="animation.svg">
		</div>

		<h1>Unerwünschte Anrufer automatisch abweisen</h1>

		<p>
			Ist Deine Telefonnummer auch zwielichtigen Addresshändlern in die
			Hände gefallen und Du erhälst ständig lästige Anrufe, die Deine
			Stromrechnung "optimieren", Dir eine Beratung für eine Solaranlage
			angedeihen lassen wollen, schon wieder einen Hauptgewinn ankündigen,
			oder sontige "Vergünstigungen" offerieren wollen? Dann ist <b>PhoneBlock</b>
			genau das was Du brauchst!
		</p>

		<h2>Wie funktioniert PhoneBlock?</h2>

		<p>PhoneBlock ist eine von der PhoneBlock-Community gepflegte
			Telefonliste mit Telefonnummern von Spam-Anrufern. Diese
			Telefonliste wird in Deinem Internet-Router "FRITZ!Box" als
			Sperrliste eingerichtet. Sobald eine Nummer in diese Sperrliste
			aufgenommen ist, weist Deine FRITZ!Box Anrufer mit dieser Nummer
			automatisch ab. Das Telefon bleibt stumm.</p>

		<p>Erhälst Du trotzdem noch einen unerwünschten Anruf, weil die
			Nummer noch nicht in die Sperrliste aufgenommen ist, kannst Du die
			Nummer ganz einfach in Deiner FRITZ!Box mit auf die Sperrliste
			setzten. Sobald Du das getan hast, aktualisiert Deine FrizBox die
			Sperrliste für alle Mitglieder der PhoneBlock-Community und Anrufe
			von dieser neuen Nummer blitzen bei allen anderen ebenfalls sofort
			ab.</p>

		<h2>Was sind die Vorausetzungen?</h2>

		<div style="float: right;">
			<a href="https://avm.de/produkte/fritzbox/"> <img id="fritzbox" width="200" alt="AVM Fritz!Box 7590"
				src="<%=request.getContextPath() %>/fritzbox.png" />
			</a>
		</div>
		
		<p>
			PhoneBlock funktioniert an einem "Festnetzanschluss" in
			Zusammenspiel mit einem <a href="https://avm.de/produkte/fritzbox/">"FRITZ!Box"
				Internetrouter von AVM</a>. Es muss nicht unbedingt das neuste Modell
			sein, aber Du solltest prüfen, ob das aktuelle FRITZ!OS darauf
			installiert ist (07.29 oder neuer). Ist dies nicht der Fall, prüfe
			anhand der Installationsanleitung, ob Deine Version die notwendigen
			Optionen schon bietet.
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a id="search-fritzbox">
				<button class="button is-medium is-info is-fullwidth" onclick="return checkFritzBox('<%=request.getContextPath() %>', this);">Fritz!Box suchen</button>
			</a>
		  </div>
		</div>

		<p>
			Du bist nicht sicher, ob Du eine Fritz!Box hast? Wenn Du Dich gerade zu Hause im WLan befindest, dann drück
			den Such-Button oben. PhoneBlock versucht dann, eine Fritz!Box in Deiner Nähe zu finden.
		</p>
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
				<p class="title">Noch Fragen?</p>
				<p class="subtitle">Check die Installationsanleitung!</p>
			</a>
		</div>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>