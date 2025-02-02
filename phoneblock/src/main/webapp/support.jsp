<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@page import="de.haumacher.phoneblock.app.SettingsServlet"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<html>
<%
	request.setAttribute("title", "PhoneBlock unterstützen");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>PhoneBlock unterstützen</h1>

		<p>
			Die Nutzung von PhoneBlock ist kostenlos, Du kannst PhoneBlock auf allen Deinen Fritz!Boxen und 
			Mobiltelefonen installieren, ohne dafür zu bezahlen. Allerdings ist der Betrieb von PhoneBlock keineswegs umsonst. 
			Für Servermiete, Internetzugang und Domainregistrierung fallen laufende Kosten an.  
			<a href="<%=request.getContextPath()%>/impressum.jsp">Ich</a> finanziere das aus der eigenen Tasche.
		</p>
		
		<p>
			Wenn Dir PhoneBlock gefällt und es Dir hilft, lästige Plagegeister von deinem Telefonanschluss fernzuhalten, dann
			kannst Du Dich an den Kosten von PhoneBlock zu beteiligen, oder mit einer kleinen Spende einfach "Danke" sagen. 
			Wenn viele Nutzer mitmachen, reicht ein geringer
			Betrag - z.B. <b>1€ pro Jahr</b> oder <b>0,01€ pro abgefangenem Spam-Anruf</b> - aus, um die Kosten zu decken. 
			Die Höhe bleibt aber Dir überlassen. Bei Deiner Spende handelt es sich nicht um einen "Mitgliedsbeitrag", und 
			Du erwartest dafür keinerlei Gegenleistung.
		</p>
<%
	String userName = LoginFilter.getAuthenticatedUser(request.getSession(false));
%>

		<div class="tile is-ancestor">
			<div class="tile is-parent is-12">
				<a class="tile is-child notification is-primary" href="http://paypal.me/phoneblock" target="_blank">
					<div class="title">
					    <span class="icon">
							<i class="fa-brands fa-paypal"></i>
					    </span>
						<span>Mit PayPal spenden</span>			
					</div>
					<p class="subtitle">
						Nutze "Geld an Freunde senden", PayPal berechnet ansonsten absurde Gebühren, bei 1€ kommen nur 60Ct an.
					</p>
<% if (userName == null) { %>					
					<p class="subtitle">
						Gib bitte die ersten paar Zeichen deines Nutzernamens mit als Nachricht ein, damit ich die Zahlung zuordnen kann.
					</p>
<% } else { %>
					<p class="subtitle">
						Bitte gibt <code id="purpose">PhoneBlock-<%= userName.substring(0, 13)%></code><span id="purpose_" title="In die Zwischenablage kopieren." href="#" class="copyToClipboard"><i class="fa-solid fa-copy"></i></span> mit als Nachricht ein, damit ich die Zahlung zuordnen kann.
					</p>
<% } %>					
				</a>
			</div>
		</div>
		
		<div class="tile is-ancestor">
			<div class="tile is-parent is-6 ">
				<a class="tile is-child notification is-info" href="https://github.com/sponsors/haumacher" target="_blank">
					<p class="title">
					    <span class="icon">
							<i class="fa-brands fa-github"></i>
					    </span>
						<span>Werde Sponsor bei GitHub</span>
					</p>
					<p class="subtitle">
						Du kannst einmalig oder regelmäßig spenden.
					</p>
				</a>
			</div>
			
			<div class="tile is-parent is-6 ">
				<a class="tile is-child notification is-info" href="<%=request.getContextPath()%>/support-banktransfer.jsp">
					<p class="title">
					    <span class="icon">
							<i class="fa-solid fa-building-columns"></i>
					    </span>
						<span>Per Bank-Überweisung spenden</span>
					</p>
					<p class="subtitle">
						Du musst Dich anmelden, um die Kontodaten zu sehen. 
					</p>
				</a>
			</div>
		</div>
		
		<p>
			Vielen Dank, dass Du Dich an den Kosten von PhoneBlock beteiligst! 
		</p>
		
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>
