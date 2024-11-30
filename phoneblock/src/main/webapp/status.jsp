<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.db.model.NumberInfo"%>
<%@page import="de.haumacher.phoneblock.db.DBNumberInfo"%>
<%@page import="de.haumacher.phoneblock.db.model.SearchInfo"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.app.LoginFilter"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.db.Status"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="de.haumacher.phoneblock.db.model.SpamReport"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="java.util.Date"%>
<%@page import="java.util.List"%>
<%@page import="java.text.DateFormat"%>
<%@page import="java.text.SimpleDateFormat"%>
<html>
<% 
request.setAttribute("title", "Telefonnummern aktueller Werbeanrufer - PhoneBlock schafft Ruhe"); 
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<%
	String userName = LoginFilter.getAuthenticatedUser(request.getSession(false));
%>

<section class="section">
<div class="content">
	<h1>Aktuelle Berichte über unerwünschte Anrufer</h1>
	
	<p>
		Unerwünschte Telefonanrufe kannst Du <em>automatisch blockieren</em>, wenn Du PhoneBlock installierst.
	</p>
	
<%
	String userAgent = request.getHeader("User-Agent");
	boolean android = userAgent != null && userAgent.toLowerCase().contains("android");
	boolean iphone = userAgent != null && userAgent.toLowerCase().contains("iPhone");
	String setupAndroidClass = android ? "is-primary" : "is-info";
	String setupIphoneBoxClass = iphone ? "is-primary" : "is-info";
	String setupFritzBoxClass = (android || iphone) ? "is-info" : "is-primary";
%>

		<div class="columns">
		  <div class="column is-one-quarter">
			<a class="button is-medium <%= setupFritzBoxClass %> is-fullwidth" href="<%=request.getContextPath()%>/anrufbeantworter/">
			    <span class="icon">
					<img src="<%=request.getContextPath()%>/anrufbeantworter/logo/ab-logo-white.svg"/>
			    </span>
				<span>Anrufbeantworter</span>			
			</a>
		  </div>
		  <div class="column is-one-quarter">
			<a class="button is-medium <%= setupFritzBoxClass %> is-fullwidth" href="<%=request.getContextPath()%>/setup.jsp">
			    <span class="icon">
					<i class="fa-solid fa-phone"></i>
			    </span>
				<span>Fritz!Box Telefonbuch</span>			
			</a>
		  </div>
		  <div class="column is-one-quarter">
			<a class="button is-medium <%= setupAndroidClass %> is-fullwidth" href="<%=request.getContextPath()%>/setup-android/">
			    <span class="icon">
					<i class="fa-solid fa-mobile-screen"></i>
			    </span>
				<span>Für Android</span>
			</a>
		  </div>
		  <div class="column is-one-quarter">
			<a class="button is-medium <%= setupIphoneBoxClass %> is-fullwidth" href="<%=request.getContextPath()%>/setup-iphone/">
			    <span class="icon">
					<i class="fa-brands fa-apple"></i>
			    </span>
				<span>Für iPhone</span>
			</a>
		  </div>
		</div>

	<p>
		Bei solchen Anrufen bleibt Dein Telefon dann stumm. Täglich kommen mehrere Nummern dazu. Teile die <a href="<%= request.getContextPath()%>/">PhoneBlock-Seite</a> z.B. auf FaceBook, 
		damit möglichst viele mitmachen und so der Telefonterror endet.
	</p>
	
<%
	DateFormat format = SimpleDateFormat.getDateTimeInstance();
	long now = System.currentTimeMillis();
%>

<%
	List<? extends SearchInfo> searches = DBService.getInstance().getTopSearches();
	if (!searches.isEmpty()) {
%>
		<h2>Top-Suchanfragen</h2> 

		<table class="table">
			<thead>
				<tr>
					<th>Rufnummer</th>
					<th title="Anzahl an Suchanfragen nach dieser Nummer seit gestern">Heute und gestern</th>
					<th title="Insgesammt gestellte Suchanfragen nach dieser Nummer">Gesamt</th>
					<th>Letzte Anfrage</th>
				</tr>
			</thead>
			<tbody>
<%			
				for (SearchInfo report : searches) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" data-onclick="showNumber">☎ <%= JspUtil.quote(report.getPhone()) %></a>
						</td>
						
						<td>
							<%=report.getCount()%>
						</td>
						
						<td>
							<%=report.getTotal()%>
						</td>
						
						<td>
							<%= report.getLastSearch() > 0 ? format.format(new Date(report.getLastSearch())) : "-" %>
						</td>
					</tr>
<%	
				}
%>
			</tbody>
		</table>
<%
	}
%>

<%
	List<? extends NumberInfo> reports = DBService.getInstance().getLatestSpamReports(System.currentTimeMillis() - 60 * 60 * 1000);
	if (!reports.isEmpty()) {
%>
		<h2>Spam-Reports der letzten Stunde</h2> 

		<table class="table">
			<thead>
				<tr>
					<th>Rufnummer</th>
					<th title="Je größer die Zahl, desto sicherer ist PhoneBlock, dass die Rufnummer ein Quelle von unerwünschten Anrufen ist.">Konfidenz</th>
					<th>Berichtet</th>
					<th title="Datum, ab dem die Nummer zum ersten Mal berichtet wurde.">Aktiv seit</th>
				</tr>
			</thead>
			<tbody>
<%			
				for (NumberInfo report : reports) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" data-onclick="showNumber">☎ <%= JspUtil.quote(report.getPhone()) %></a>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= (now - report.getUpdated()) / 1000 / 60 %> minutes ago
						</td>
						
						<td>
							<%= report.getAdded() > 0 ? format.format(new Date(report.getAdded())) : "-" %>
						</td>
					</tr>
<%	
				}
%>
			</tbody>
		</table>
<%	
	}
%>

<%
	reports = DBService.getInstance().getLatestBlocklistEntries(userName);
	if (!reports.isEmpty()) {
%>
		<h2>Neuste Einträge in der Block-List</h2> 

		<table class="table">
			<thead>
				<tr>
					<th>Rufnummer</th>
					<th title="Je größer die Zahl, desto sicherer ist PhoneBlock, dass die Rufnummer ein Quelle von unerwünschten Anrufen ist.">Konfidenz</th>
					<th>Letze Beschwerde</th>
					<th title="Datum, ab dem die Nummer zum ersten Mal berichtet wurde.">Aktiv seit</th>
				</tr>
			</thead>
			<tbody>
<%			
				for (NumberInfo report : reports) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" data-onclick="showNumber">☎ <%= JspUtil.quote(report.getPhone()) %></a>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= format.format(new Date(report.getUpdated()))%>
						</td>

						<td>
							<%= report.getAdded() > 0 ? format.format(new Date(report.getAdded())) : "-" %>
						</td>
					</tr>
<%	
				}
%>
			</tbody>
		</table>
<%	
	}
%>

<%
	reports = DBService.getInstance().getTopSpamReports(15);
	if (!reports.isEmpty()) {
%>
		<h2>Aktuelle Top-Spammer</h2> 

		<table class="table">
			<thead>
				<tr>
					<th>Rufnummer</th>
					<th title="Je größer die Zahl, desto sicherer ist PhoneBlock, dass die Rufnummer ein Quelle von unerwünschten Anrufen ist.">Konfidenz</th>
					<th>Letze Beschwerde</th>
					<th title="Datum, ab dem die Nummer zum ersten Mal berichtet wurde.">Aktiv seit</th>
				</tr>
			</thead>
			<tbody>
<%			
				for (NumberInfo report : reports) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" data-onclick="showNumber">☎ <%= JspUtil.quote(report.getPhone()) %></a>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= format.format(new Date(report.getUpdated()))%>
						</td>

						<td>
							<%= report.getAdded() > 0 ? format.format(new Date(report.getAdded())) : "-" %>
						</td>
					</tr>
<%	
				}
%>
			</tbody>
		</table>
<%	
	}
%>

	<h2>PhoneBlock-Datenbank</h2>

	<p>
		Bekannte SPAM-Nummern: 
<%
	Status status = DBService.getInstance().getStatus(userName);
	List<Statistics> statistic = status.getStatistics();
	int cnt = 0;
	String[] labels = {"berichtet", "bestätigt", "sicher"};
	
	for (Statistics statistics : statistic) {
		// Exclude numbers reported only once.
		if (statistics.getConfidence() > 0) {
			cnt += statistics.getCnt();
		}
		String label = labels[statistics.getConfidence()];
%>		
		<%= statistics.getCnt() %> <%= JspUtil.quote(label) %>,
<%
	}
%>	
	<%= cnt %> aktive Nummern auf der Blocklist. Insgesamt <%= status.getTotalVotes() %> User-Reports, <%= status.getArchivedReports() %> 
	inaktive Nummern mit Spam-Verdacht.
	</p>

</div>

<div class="tile is-ancestor">
	<div class="tile is-parent is-6">
		<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/">
			<p class="title">Noch kein PhoneBlock?</p>
			<p class="subtitle">So wirst du den Telefonterror los!</p>
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