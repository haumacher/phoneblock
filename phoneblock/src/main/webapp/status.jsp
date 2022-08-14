<!DOCTYPE html>
<%@page import="java.util.Date"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="java.text.DateFormat"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="java.util.List"%>
<%@page import="de.haumacher.phoneblock.db.SpamReport"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
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
	<p>
		Bekannte SPAM-Nummern: 
<%
	List<Statistics> statistic = DBService.getInstance().getSpamReportStatistic();
	int cnt = 0;
	String[] labels = {"berichtet", "bestätigt", "sicher"};
	
	for (Statistics statistics : statistic) {
		cnt += statistics.getCnt();
		String label = labels[statistics.getContidence()];
%>		
		<%= statistics.getCnt() %> <%= JspUtil.quote(label) %>,
<%
	}
%>	
	insgesammt <%= cnt %> Nummern.
	</p>

<%
	long now = System.currentTimeMillis();
	List<SpamReport> reports = DBService.getInstance().getLatestSpamReports(System.currentTimeMillis() - 60 * 60 * 1000);
	if (reports.isEmpty()) {
%>
		<p>Keine aktuellen Spam-Reports.</p>
<%			
	} else {
%>
		<h2>Spam-Reports der letzten Stunde</h2> 

		<table class="table">
			<thead>
				<tr>
					<th>Phone number</th>
					<th>Confidence</th>
					<th>Received</th>
				</tr>
			</thead>
			<tbody>
<%			
				for (SpamReport report : reports) {
%>
					<tr>
						<td>
							<%= JspUtil.quote(report.getPhone()) %>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= (now - report.getLastUpdate()) / 1000 / 60 %> minutes ago
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
	reports = DBService.getInstance().getTopSpamReports();
	if (!reports.isEmpty()) {
		DateFormat format = SimpleDateFormat.getDateTimeInstance();
%>
		<h2>Aktuelle Top-Spammer</h2> 

		<table class="table">
			<thead>
				<tr>
					<th>Phone number</th>
					<th>Confidence</th>
					<th>Last update</th>
				</tr>
			</thead>
			<tbody>
<%			
				for (SpamReport report : reports) {
%>
					<tr>
						<td>
							<%= JspUtil.quote(report.getPhone()) %>
						</td>
						
						<td>
							<%= report.getVotes() %>
						</td>
						
						<td>
							<%= format.format(new Date(report.getLastUpdate()))%>
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
</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>