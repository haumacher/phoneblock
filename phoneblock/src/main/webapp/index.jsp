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
		<div style="float: right;">
			<img alt="logo" src="animation.svg">
		</div>

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
			<a href="https://avm.de/produkte/fritzbox/"> <img width="200"
				src="https://avm.de/fileadmin/user_upload/Global/Produkte/FRITZBox/7590/fritzbox_7590_left_de_stiftung_warentest_640x400.png" />
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

		<p>Als weiteres benötigst Du nur noch einen PhoneBlock-Account,
			mit dem Du die Telefonsperrliste in Deiner FRITZ!Box einrichten
			kannst.</p>
	</div>

	<div class="tile is-ancestor">
		<div class="tile is-parent is-6">
			<a class="tile is-child notification is-primary" href="signup.jsp">
				<p class="title">Klar zum Loslegen?</p>
				<p class="subtitle">Dann mach einen PhoneBlock-Account!</p>
			</a>
		</div>

		<div class="tile is-parent is-6 ">
			<a class="tile is-child notification is-info" href="setup.jsp">
				<p class="title">Noch Fragen?</p>
				<p class="subtitle">Check die Installationsanleitung!</p>
			</a>
		</div>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>