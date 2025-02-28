<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@page import="de.haumacher.phoneblock.app.RegistrationServlet"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	request.setAttribute("title", "Der PhoneBlock-Anrufbeantworter");
	request.setAttribute(LoginServlet.KEEP_LOCATION_AFTER_LOGIN, "true");
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="/header.jspf"></jsp:include>

<input type="hidden" id="context-path" value="<%=request.getContextPath()%>">

<section class="section">
	<div class="content">
		<div class="logo-animation">
			<img alt="PhoneBlock-Anrufbeantworter-Logo" src="logo/ab-logo.svg">
		</div>
	
		<h1>Installation als Anrufbeantworter</h1>
		
		<p>
			PhoneBlock bietet Dir einen intelligenten Anrufbeantworter an, der automatisch nur dann 
			das Gespräch annimmt, wenn die Nummer des Anrufers in der <a href="<%=request.getContextPath()%>/status.jsp">SPAM-Datenbank von PhoneBlock</a> 
			gelistet ist. Genauso wie für die <a href="<%=request.getContextPath()%>/setup.jsp">Einrichtung der Blockliste</a> benötigst Du hierfür kein 
			eigenes Gerät oder Computer. Du erstellt einen PhoneBlock-Anrufbeantworter hier auf der 
			Webseite und meldest ihn über das Internet an Deiner Fritz!Box oder einem anderen VOIP-fähigen
			Internetrouter an. 
			Wie genau der PhoneBlock-Anrufbeantworter funktioniert kannst Du 
			<a href="https://medium.com/@haui_81991/der-phoneblock-anrufbeantworter-erkl%C3%A4rt-e0c8e7a12822" target="_blank">hier nachlesen</a>.
		</p>
		
		<p>
			Mit folgenden Schritten kommst Du zu Deinem PhoneBlock-Anrufbeantworter:
		</p>
		
		<ol>
			<li><a href="#register">Bei PhoneBlock anmelden</a></li>
			<li><a href="#create">Anrufbeantworter erstellen</a></li>
			<li><a href="#dyndns">DynDNS in der Fritz!Box einrichten</a></li>
			<li><a href="#sip">Telefoniegerät in der Fritz!Box einrichten</a></li>
			<li><a href="#enable">PhoneBlock-Anrufbeantworter einschalten</a></li>
			<li><a href="#test">Anrufbeantworter testen</a></li>
		</ol>
		
		 <p>
		 	Die Anleitung führt Dich Schritt für Schritt durch die Einrichtung. Die Konfiguration 
		 	in der Fritz!Box ist nicht ganz einfach, aber wenn Du Dich genau an das hier beschriebene
		 	Vorgehen hälst, kann eigentlich nichts schief gehen. 
		 </p>
		 
		<h2 id="register">Schritt 1: Bei PhoneBlock anmelden</h2>

		<p>
			Ab hier benötigst Du drei Browser-Fenster (oder Reiter). Eines für diese Anleitung, eines für die PhoneBlock-Anrufbeantworter-App 
			und eines für die Konfigurationsoberfläche Deiner Fritz!Box. 
		</p>
		
<%
	String userName = LoginFilter.getAuthenticatedUser(request.getSession(false));
	if (userName == null) { 
%>
		<p>
		Als erstes musst Du Dich bei PhoneBlock registrieren bzw. anmelden, um mit der Einrichtung zu beginnen.
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a class="button is-medium is-info is-fullwidth" href="<%=request.getContextPath()%>/login.jsp?locationAfterLogin=/anrufbeantworter/#create">
			    <span class="icon">
					<i class="fa-solid fa-right-to-bracket"></i>
			    </span>
				<span>Anmelden</span>			
			</a>
		  </div>
		</div>
<% } else { %>
		<p>
		Du bist als <code><%= JspUtil.quote(userName)%></code> angemeldet, prima, gleich zum nächsten Schritt! 
		</p>
<% }%>
		
		<h2 id="create">Schritt 2: Anrufbeantworter erstellen</h2>
		
		<p>
			Wenn Du hier klickst öffnet sich Fenster mit der <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">PhoneBlock-Anrufbeantworter-App</a>.
			Beim ersten Öffnen ist die Liste Deiner Anrufbeantworter leer und unten rechts befindet sich ein Knopf, 
			mit dem Du Dir einen Anrufbeantworter erstellen kannst. Drücke diesen Plus-Knopf.
		</p>
		
		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a class="button is-medium is-primary is-fullwidth showAB" href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab">
			    <span class="icon">
					<i class="fa-solid fa-arrow-up-right-from-square"></i>
			    </span>
				<span>App öffnen</span>			
			</a>
		  </div>
		</div>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image appscreen" alt="DynDNS einrichten" src="<%=request.getContextPath() %>/assets/img/ab/app/01-create-answerbot.png"/>
	  		</div>
		</div>
		
		<h2 id="dyndns">Schritt 4: DynDNS einrichten</h2>
		
		<p>
			Über "DynDNS" teilt Deine Fritz!Box PhoneBlock ihre IP-Adresse mit. Das ist notwendig, damit der 
			PhoneBlock-Anrufbeantworter Deine Fritz!Box findet um sich dort anzumelden.
		</p>
		
		<h3>PhoneBlock-DynDNS aktivieren</h3>
		
		<p>
			Aktiviere in der <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">Anrufbeantworter-App</a>
			den Schalter "PhoneBlock-DynDNS verwenden" (Wenn Du Dich gut auskennst und bereits einen anderen DynDNS-Provider verwendest, kannst Du auch direkt
			den Host-Namen deiner Fritz!Box eintragen).
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image appscreen" alt="DynDNS einrichten" src="<%=request.getContextPath() %>/assets/img/ab/app/02-enable-dyndns.png"/>
	  		</div>
		</div>
		
		<p>
			Auf der Folgeseite bestätige mit "DynDNS aktivieren". 
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image appscreen" alt="DynDNS einrichten" src="<%=request.getContextPath() %>/assets/img/ab/app/03-create-dyndns-account.png"/>
	  		</div>
		</div>
		
		<p>
			Jetzt erhälst Du Zugangsdaten für das PhoneBlock-DynDNS, die Du 
			in Deine Fritz!Box eintragen kannst (siehe weiter unten). 
		</p>

		<h3>Anmeldedaten in der Fritz!Box eintragen</h3>
		
		<p>
			Dieser Link <a href="http://fritz.box" target="fritzbox" class="showFB">öffnet ein Fenster für Deine Fritz!Box</a>.
			Melde dich dort mit Deinem Fritz!Box-Kennwort an. Wenn Du das noch nie getan hast, dann findest Du 
			das Kennwort auf der Unterseite der Fritz!Box. 
		</p>
		
		<p>
			In Deiner <a href="http://fritz.box" target="fritzbox" class="showFB">Fritz!Box</a> navigiere in der Seitenleiste zu "Internet &gt; Freigaben" und wähle
			den Reiter "DynDNS".
		</p>

		<div class="columns">
		  <div class="column is-half is-offset-one-quarter">
			<a class="button is-medium is-primary is-fullwidth showFB" href="http://fritz.box" target="fritzbox">
			    <span class="icon">
					<i class="fa-solid fa-arrow-up-right-from-square"></i>
			    </span>
				<span>Fritz!Box öffnen</span>			
			</a>
		  </div>
		</div>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="DynDNS einrichten" src="<%=request.getContextPath() %>/assets/img/ab/setup/02-configure-dyndns.png"/>
	  		</div>
		</div>
		
		<p>
			Kopiere aus der <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">Anrufbeantworter-App</a> die Daten für
			"Update-Url", "Domainname", "Benutzername" und "Kennwort" und trage sie in Deiner <a href="http://fritz.box" target="fritzbox" class="showFB">Fritz!Box</a>
			ein. Und bestätige mit "Übernehmen" (Punkt 5 im Bild unten). 
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="DynDNS einrichten" src="<%=request.getContextPath() %>/assets/img/ab/app/04-copy-dyndns-settings.png"/>
	  		</div>
		</div>
		
		<h3>DynDNS überprüfen</h3>

		<p>
			Gehe jetzt zurück zu der <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">Anrufbeantworter-App</a> und
			lass die Einstellungen überprüfen (Punkt 6 im Bild oben).
			Wenn PhoneBlock Deine Fritz!Box gefunden hat, kommst zu zur nächsten Seite mit den Zugangsdaten für den eigentlichen Anrufbeantworter.
		</p>
		
		<h2 id="sip">Schritt 4: Anrufbeantworter in der Fritz!Box einrichten</h2>
		
		<p>
			Jetzt wird der Anrufbeantworter als Telefoniegerät in der Fritz!Box eingerichtet. Öffne hierzu wieder 
			Deine <a href="http://fritz.box" target="fritzbox" class="showFB">Fritz!Box-Oberfläche</a>.
		</p>

		<h3>Telefoniegerät einrichten</h3>

		<p>
			Navigiere in der Seitenleiste zu "Telefonie > Telefoniegeräte" und klicke den Knopf "Neues Gerät einrichten".
		</p>

		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Gerät einrichten" src="<%=request.getContextPath() %>/assets/img/ab/setup/03-create-new-device.png"/>
	  		</div>
		</div>
		
		<h3>Art des Gerätes wählen</h3>

		<p>
			Wähle die Option "Telefon (mit und ohne Anrufbeantworter)" und Klicke auf "Weiter". Hinweis: Wähle <b>nicht</b> die 
			an sich naheliegende Option "Anrufbeantworter", da man hiermit nur Anrufbeantworter konfigurieren kann, die in der 
			Fritz!Box fest eingebaut sind.  
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Gerät erstellen" src="<%=request.getContextPath() %>/assets/img/ab/setup/04-create-phone.png"/>
	  		</div>
		</div>

		<h3>IP-Telefon konfigurieren</h3>

		<p>
			Auf der Seite "Telefon anschließen" wähle die Option "LAN/WLAN (IP-Telefon)" und gibt dem neuen Gerät den Namen 
			"PhoneBlock" im Text-Feld unten. Klicke danach auf "Weiter".
		</p>

		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="IP-Telefon erstellen" src="<%=request.getContextPath() %>/assets/img/ab/setup/05-choose-ip-phone.png"/>
	  		</div>
		</div>

		<h3>Zugangsdaten übernehmen</h3>

		<p>
			Auf der nächsten Seite "Einstellungen im IP-Telefon übernehmen" wirst Du nach "Benutzername" und "Kennwort" gefragt. 
			Kopiere hier die entsprechenden Daten, die Dir in der <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">Anrufbeantworter-App</a>
			angezeigt werden. Klicke auf "Weiter". 
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Benutzerdaten" src="<%=request.getContextPath() %>/assets/img/ab/app/05-copy-sip-settings.png"/>
	  		</div>
		</div>
		
		<h3>Einstellungen für ausgehende Gespräche</h3>

		<p>
			Auf der Seite "Telefon für ausgehende Gespräche einrichten" kannst Du einfach "Weiter" klicken. 
			Der PhoneBlock-Anrufbeantworter benötigt keine Rufnummer für ausgehende Gespräche, weil er nie aktiv Gespräche führt. 
			Wir deaktivieren diese Rufnummer in einem späteren Schritt, damit Deine Anrufbeantworter auch wirklich keine
			Gespräche führen kann.
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Ausgehende Gespräche" src="<%=request.getContextPath() %>/assets/img/ab/setup/07-choose-local-number.png"/>
	  		</div>
		</div>

		<h3>Zu schützende Nummern auswählen</h3>

		<p>
			Auf der Seite "Telefon-Einstellungen für ankommende Gespräche einrichten" solltest Du die Standardeinstellung 
			"alle Anrufe annehmen" belassen. Wenn Du nur bestimmte Nummern vor SPAM-Anrufen schützen möchtest, kannst Du hier
			auch die Nummern auswählen, für die der Anrufbeantworter überhaupt einen Anruf zu sehen bekommt. Klicke auf "Weiter".
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Geschützte Nummern" src="<%=request.getContextPath() %>/assets/img/ab/setup/08-numbers-to-protect.png"/>
	  		</div>
		</div>

		<h3>Konfiguration bestätigen</h3>

		<p>
			Du kommst jetzt auf die Zusammenfassungsseite "Einstellungen übernehmen". Du kannst die vorgenommenen Einstellungen 
			nochmal überprüfen und mit "Übernehmen" bestätigen.
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Einstellungen prüfen" src="<%=request.getContextPath() %>/assets/img/ab/setup/10-check-settings.png"/>
	  		</div>
		</div>

		<h3>Gerät fertig konfigurieren</h3>

		<p>
			Du bist zurück in der "Geräteübersicht". Hier solltest Du jetzt Deinen neuen Anrufbeantworter "PhoneBlock" in 
			der Liste sehen. Bevor Du den Anrufbeantworter aber einschalten kannst, müssen noch weitere Einstellungen vorgenommen werden. 
			Klicke hierzu auf den Bearbeiten-Stift am Ende der PhoneBlock-Zeile.
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Anrufbeantworter bearbeiten" src="<%=request.getContextPath() %>/assets/img/ab/setup/09-edit-phone.png"/>
	  		</div>
		</div>

		<h3>Ausgehende Anrufe unterbinden</h3>

		<p>
			In dem Auswahlfeld "Ausgehende Anrufe" kannst Du jetzt den letzten leeren Eintrag wählen, um sicherzustellen, dass
			Dein Anrufbeantworter nicht selbständig Gespräche führen kann. Klicke <b>nicht</b> auf den Übernehmen-Knopf unten, 
			sondern auf den zweiten Reiter "Anmeldedaten". Jetzt wirst Du gefragt, ob du die Änderungen speichern möchtest. 
			Klicke in diesem Dialog auf "Übernehmen".
		</p>
		
		<div class="columns">
			<div class="column">
	  			<img class="image" alt="Ausgehende Anrufe verhindern" src="<%=request.getContextPath() %>/assets/img/ab/setup/11-prevent-call.png"/>
	  		</div>
			<div class="column">
	  			<img class="image" alt="Bestätigen" src="<%=request.getContextPath() %>/assets/img/ab/setup/12-confirm.png"/>
	  		</div>
		</div>

		<h3>Anmeldung aus dem Internet erlauben</h3>

		<p>
			Im Reiter "Anmeldedaten" musst Du jetzt noch die Option "Anmeldung aus dem Internet erlauben" aktivieren und das Kennwort 
			erneut aus der <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">Anrufbeantworter-App</a> kopieren und in das Feld "Kennwort" eintragen.
			Klicke erst dann auf den Knopf "Übernehmen".
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Zugriff aus dem Internet" src="<%=request.getContextPath() %>/assets/img/ab/setup/13-allow-internet-access.png"/>
	  		</div>
		</div>
		
		<h2 id="enable">Schritt 5: Anrufbeantworter einschalten</h2>
		
		<p>
			Die Konfiguration Deines Anrufbeantworters ist jetzt abgeschlossen. Wechsele zurück in die <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">Anrufbeantworter-App</a>
			und schalte Deinen neuen Anrufbeantworter ein.
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image appscreen" alt="Zugriff aus dem Internet" src="<%=request.getContextPath() %>/assets/img/ab/app/06-register-answerbot.png"/>
	  		</div>
		</div>
		
		<p>
			Gratuliere, wenn alles geklappt hat, sollte der Anrufbeantworter in der <a href="<%=request.getContextPath() %>/ab/" target="phoneblock-ab" class="showAB">Anrufbeantworter-App</a>
			jetzt grün als "eingeschaltet" angezeigt werden. Die nächsten unliebsamen Anrufer können in Zukunft mit PhoneBlock diskutieren.
		</p>

		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image appscreen" alt="Anrufbeantworter verbunden" src="<%=request.getContextPath() %>/assets/img/ab/app/10-answerbot-activated.png"/>
	  		</div>
		</div>
		
		<h2 id="test">Schritt 6: Anrufbeantworter testen</h2>

		<p>
			Wenn Du ausprobieren willst, wie ein Gespräch mit dem nicht ganz alltäglichen PhoneBlock-Anrufbeantworter abläuft, kannst Du ihn 
			von einem direkt an der Fritz!Box angeschlossenen Telefon (z.B. einem DECT-Schnurlostelefon) über die <b>interne Nummer</b> des Anrufbeantworters anrufen. 
			Solche Test-Anrufe nimmter der Anrufbeantworter immer entgegen. <b>Wichtig:</b> Das funktioniert <b>nur</b> über die interne Nummer des 
			Anrufbeantworters (fängt normalerweise mit "**" an). Die interne Nummer findest Du in der Geräteübersicht Deiner Fritz!Box in der 
			Zeile "PhoneBlock". Probier doch mal, ihm eine Photovoltaikanlage aufzuschätzen. Viel Spaß!
		</p>
		
		<div class="columns">
			<div class="column is-8 is-offset-2">
	  			<img class="image" alt="Anrufbeantworter verbunden" src="<%=request.getContextPath() %>/assets/img/ab/setup/14-answerbot-local-number.png"/>
	  		</div>
		</div>
		
	</div>
</section>
<script type="text/javascript" src="anrufbeantworter.js"></script>
<jsp:include page="../footer.jspf"></jsp:include>
</body>
</html>