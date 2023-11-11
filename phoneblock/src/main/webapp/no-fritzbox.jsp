<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<html>
<head>
<link rel="canonical" href="https://phoneblock.net/phoneblock/" />
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>Keine Fritz!Box gefunden</h1>

		<p>
			Die Suche hat keine Fritz!Box gefunden. Das kann mehrere Gründe haben.
		</p>
		
		<ul>
			<li>Du bist nicht im WLan eingebucht, sind vielleicht "mobile Daten" aktiv?</li>
			<li>Deine Fritz!Box hat nicht die Standard-Konfiguration mit Name <code>fritz.box</code> und Adresse <code>192.168.178.1</code>?
			<li>Du hast einen anderen Internet-Router. Vielleicht kann er auch mit PhoneBlock zusammenarbeiten, check die <a href="<%=request.getContextPath() %>/faq.jsp">FAQ</a>!</li>
		</ul>
	</div>

	<div class="tile is-ancestor">
		<div class="tile is-parent is-6">
			<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/faq.jsp">
				<p class="title">Nichts gefunden?</p>
				<p class="subtitle">Check die FAQ-Liste!</p>
			</a>
		</div>

		<div class="tile is-parent is-6 ">
			<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/setup.jsp">
				<p class="title">Trotzdem ausprobieren?</p>
				<p class="subtitle">Check die Installationsanleitung!</p>
			</a>
		</div>
	</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>