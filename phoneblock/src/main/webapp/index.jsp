<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.db.SpamReport"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<html>
<head>
<title>The spam blocker for your phone</title>
</head>
<body>
<h1>The spam blocker for your phone.</h1>

<p>
The spam reports received in the last hour are listed below.
</p>

<table>
	<thead>
		<tr>
			<th>Phone number</th>
			<th>Confidence</th>
			<th>Received</th>
		</tr>
	</thead>
	<tbody>
<%
		long now = System.currentTimeMillis();
		for (SpamReport report : DBService.getInstance().getLatestSpamReports(System.currentTimeMillis() - 60 * 60 * 1000)) {
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

</body>
</html>