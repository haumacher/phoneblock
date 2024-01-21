<!DOCTYPE html>
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
		Unerwünschte Telefonanrufe kannst Du <em>automatisch blockieren</em>, wenn Du <a href="setup.jsp">PhoneBlock installierst</a>.
		Bei solchen Anrufen bleibt Dein Telefon dann stumm. Täglich kommen mehrere Nummern dazu. Poste Links auf die <a href="<%= request.getContextPath()%>/">PhoneBlock-Seite</a>, 
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
					<th title="Suchanfragen nach dieser Nummer heute">Heute</th>
					<th title="Insgesammt gestellte Suchanfragen nach dieser Nummer">Gestern</th>
					<th>Letzte Anfrage</th>
				</tr>
			</thead>
			<tbody>
<%			
				for (SearchInfo report : searches) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" onclick="return showNumber('<%= report.getPhone()%>');">☎ <%= JspUtil.quote(report.getPhone()) %></a>
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
	List<? extends SpamReport> reports = DBService.getInstance().getLatestSpamReports(System.currentTimeMillis() - 60 * 60 * 1000);
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
				for (SpamReport report : reports) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" onclick="return showNumber('<%= report.getPhone()%>');">☎ <%= JspUtil.quote(report.getPhone()) %></a>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= (now - report.getLastUpdate()) / 1000 / 60 %> minutes ago
						</td>
						
						<td>
							<%= report.getDateAdded() > 0 ? format.format(new Date(report.getDateAdded())) : "-" %>
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
				for (SpamReport report : reports) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" onclick="return showNumber('<%= report.getPhone()%>');">☎ <%= JspUtil.quote(report.getPhone()) %></a>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= format.format(new Date(report.getLastUpdate()))%>
						</td>

						<td>
							<%= report.getDateAdded() > 0 ? format.format(new Date(report.getDateAdded())) : "-" %>
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
				for (SpamReport report : reports) {
%>
					<tr>
						<td>
							<a href="<%= request.getContextPath()%>/nums/<%= report.getPhone()%>" onclick="return showNumber('<%= report.getPhone()%>');">☎ <%= JspUtil.quote(report.getPhone()) %></a>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= format.format(new Date(report.getLastUpdate()))%>
						</td>

						<td>
							<%= report.getDateAdded() > 0 ? format.format(new Date(report.getDateAdded())) : "-" %>
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