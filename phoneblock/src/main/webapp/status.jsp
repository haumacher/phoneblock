<!DOCTYPE html>
<%@page pageEncoding="UTF-8" %>
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
<%
	long now = System.currentTimeMillis();
	List<SpamReport> reports = DBService.getInstance().getLatestSpamReports(System.currentTimeMillis() - 60 * 60 * 1000);
	if (reports.isEmpty()) {
%>
		<p>No recent spam reports.</p>
<%			
	} else {
%>
		<p>
		The spam reports received in the last hour are listed below.
		</p>

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
</div>
</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>