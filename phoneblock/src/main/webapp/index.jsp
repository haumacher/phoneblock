<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<%
	request.setAttribute("keywords", "Telefonterror, Telefonspam, angerufen, Werbung, Hotline");
%>
<html>
<head>
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

<%
	String userAgent = request.getHeader("User-Agent");
	boolean android = userAgent != null && userAgent.toLowerCase().contains("android");
	String setupAndroidClass = android ? "is-primary" : "is-info";
	String setupFritzBoxClass = android ? "is-info" : "is-primary";
%>

		<div class="columns">
		  <div class="column is-half">
			<a class="button is-medium <%= setupAndroidClass %> is-fullwidth" href="<%=request.getContextPath()%>/setup-android/">
			    <span class="icon">
					<i class="fa-solid fa-mobile-screen"></i>
			    </span>
				<span>PhoneBlock für Android</span>
			</a>
		  </div>
		  <div class="column is-half">
			<a class="button is-medium <%= setupFritzBoxClass %> is-fullwidth" href="<%=request.getContextPath()%>/setup.jsp">
			    <span class="icon">
					<i class="fa-solid fa-phone"></i>
			    </span>
				<span>PhoneBlock für Fritz!Box</span>			
			</a>
		  </div>
		</div>

		<h2>Wie funktioniert PhoneBlock?</h2>

		<p>PhoneBlock ist eine von der PhoneBlock-Community gepflegte
			Liste mit Telefonnummern, die unter Spamverdacht stehen. Diese
			Telefonliste wird in Deinem Internet-Router "FRITZ!Box" als
			Sperrliste eingerichtet. So kann Deine Fritz!Box automatisch alle Anrufer 
			blockieren, die als agressive Telefonwerbung, Telefonterror, oder Telefonbetrüger 
			bekannt sind. Du musst nicht mehr von Hand Rufnummernbereiche sperren, denn Deine Fritz!Box 
			aktualisiert die Sperrliste jede Nacht automatisch. Sobald eine Nummer in diese Sperrliste
			aufgenommen ist, weist Deine FRITZ!Box Anrufer mit dieser Nummer
			automatisch ab. Das Telefon bleibt stumm.</p>

		<p>Erhälst Du trotzdem noch einen unerwünschten Anruf, weil die
			Nummer noch nicht in die Sperrliste aufgenommen ist, kannst Du die
			<a href="<%=request.getContextPath()%>/block.jsp">Nummer ganz einfach in Deiner FRITZ!Box sperren</a>. 
			Hierzu machst Du für den unerwünschten 
			Anrufer einen Eintrag im PhoneBlock-Adressbuch direkt in Deiner Fritz!Box. 
			Sobald Du das getan hast, aktualisiert Deine FrizBox die
			Sperrliste für alle Mitglieder der PhoneBlock-Community und Anrufe
			von dieser neuen Nummer blitzen bei allen anderen ebenfalls sofort
			ab. Das ist zwar ein klein wenig aufwendiger, als die Nummer in Deinem Fritz!Phone direkt 
			zu sperren, aber hat den Vorteil, dass Du damit die ganze PhoneBlock-Community vor 
			<a href="<%=request.getContextPath()%>/status.jsp">weiteren Spam-Anrufen</a> schützt. 
		</p>

		<h2>Was sind die Vorausetzungen?</h2>

		<div style="float: right;">
			<a href="<%=request.getContextPath()%>/link/fritzbox"> <img id="fritzbox" width="200" alt="AVM Fritz!Box 7590"
				src="<%=request.getContextPath() %>/fritzbox.png" />
			</a>
		</div>
		
		<p>
			PhoneBlock kann unerwünschte Anrufe entweder am <a href="<%=request.getContextPath()%>/setup.jsp">"Festnetzanschluss"</a> in
			Zusammenspiel mit einem <a href="<%=request.getContextPath()%>/link/fritzbox">"FRITZ!Box"
			Internetrouter von AVM</a> blockieren, oder auf Deinem <a href="<%=request.getContextPath()%>/setup-android/">Mobiltelefon</a> direkt anzeigen, ob ein Anrufer potentiell unerwünscht ist. 
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a id="search-fritzbox" class="button is-medium is-info is-fullwidth" href="#" onclick="return checkFritzBox('<%=request.getContextPath() %>', this);">
				<span class="icon is-small is-left">
					<i class="fa-solid fa-magnifying-glass"></i>
				</span>
				<span>Fritz!Box suchen</span>
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
			<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/faq.jsp">
				<p class="title">Noch Fragen?</p>
				<p class="subtitle">Check die FAQ!</p>
			</a>
		</div>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>