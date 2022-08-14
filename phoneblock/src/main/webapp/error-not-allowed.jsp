<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<html>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
	<jsp:include page="header.jspf"></jsp:include>

	<section class="section">
		<div class="content">

			<h2>Diese Seite kann nicht im Browser angezeigt werden</h2>

			<p>
				Deinen Addressbuch-Link, den Du bei der <a
					href="<%=request.getContextPath()%>/signup.jsp">Anmeldung</a>
				erhalten hast, musst Du wie dort beschrieben zusammen mit Deiner
				E-Mail-Adresse und dem Dir zugeschickten Passwort in Deiner
				Fritz!Box als Adressbuch eintragen. Du kannst diesen Link nicht
				direkt im Browser anzeigen, weil der Zugriff auf Adressbücher über
				ein spezielles Protokoll (CardDAV) erfolgt, das Dein Browser nicht
				unterstützt.
			</p>

			<div class="tile is-ancestor">
				<div class="tile is-parent is-6">
					<a class="tile is-child notification is-primary"
						href="<%=request.getContextPath()%>/signup.jsp">
						<p class="title">Anmeldedaten vergessen?</p>
						<p class="subtitle">Einfach nochmal registrieren!</p>
					</a>
				</div>

				<div class="tile is-parent is-6 ">
					<a class="tile is-child notification is-info"
						href="<%=request.getContextPath()%>/setup.jsp">
						<p class="title">Noch Fragen?</p>
						<p class="subtitle">Check die Installationsanleitung!</p>
					</a>
				</div>
			</div>

		</div>
	</section>

	<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>