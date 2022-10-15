<!DOCTYPE html>
<%@page import="java.util.GregorianCalendar"%>
<%@page import="java.util.Calendar"%>
<%@page import="java.util.ArrayList"%>
<%@page import="de.haumacher.phoneblock.db.model.SearchInfo"%>
<%@page import="java.util.Map"%>
<%@page import="de.haumacher.phoneblock.db.model.RatingInfo"%>
<%@page import="de.haumacher.phoneblock.app.Ratings"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.db.model.Rating"%>
<%@page import="de.haumacher.phoneblock.analysis.PhoneNumer"%>
<%@page import="de.haumacher.phoneblock.analysis.NumberAnalyzer"%>
<%@page import="java.util.Date"%>
<%@page import="java.text.DateFormat"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Locale"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="de.haumacher.phoneblock.db.DB"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.db.SpamReport"%>
<%@page import="de.haumacher.phoneblock.db.Status"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>

<%
	String userAgent = request.getHeader("User-Agent");
	boolean android = userAgent != null && userAgent.toLowerCase().contains("android");
	PhoneNumer analysis = (PhoneNumer) request.getAttribute("number");
	SpamReport info = (SpamReport) request.getAttribute("info");
	Rating rating = (Rating) request.getAttribute("rating");
	Map<Rating, Integer> ratings = (Map<Rating, Integer>) request.getAttribute("ratings");
	List<? extends SearchInfo> searches = (List<? extends SearchInfo>) request.getAttribute("searches");
	if (android) {
		ratings.remove(Rating.B_MISSED);
	}
	int complaints = (info.getVotes() + 1) / 2;
	
	boolean thanks = request.getAttribute("thanks") != null;
%>

<head>
<jsp:include page="head-content.jspf"></jsp:include>

<% if (!ratings.isEmpty() || !searches.isEmpty()) { %>
<script type="text/javascript" src="<%=request.getContextPath() %>/webjars/chartjs/3.9.1/dist/chart.min.js"></script>
<% } %>

</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
<div class="content">
	<h1>☎ <%= info.getPhone()%></h1>

<%
	if (info.getVotes() == 0) {
%>
	<p>
		<span class="tag is-info is-success">Keine Beschwerden</span>
	</p>

	<p>
		Die Telefonnummer ist nicht in der <a href="<%=request.getContextPath() %>/">PhoneBlock</a>-Datenbank vorhanden. 
		Es gibt bisher keine Beschwerden über unerwünschte Anrufe von ☎ <code><%= info.getPhone() %></code>.
	</p>

<%
	} else {
%>		
<% 
		if (info.getVotes() < DB.MIN_VOTES || info.isArchived()) {
%>
	<p>
		<span class="tag is-info is-warning">Beschwerde liegt vor</span>
<% if (rating != Rating.B_MISSED) { %>
		<span class="tag is-info <%= Ratings.getCssClass(rating)%>"><%= Ratings.getLabel(rating)%></span>
<% } %>		
	</p>

	<p>
		Es gibt bereits <% if (complaints == 1) { %>eine Beschwerde<%} else {%><%= complaints %> Beschwerden<%}%> über unerwünschte Anrufe von 
		☎ <code><%= info.getPhone() %></code>. Die Nummer wird aber noch nicht blockiert. 
	</p>

<%
		} else {
%>			
	<p>
		<span class="tag is-info is-danger">Blockiert</span>
<% if (rating != Rating.B_MISSED) { %>
		<span class="tag is-info <%= Ratings.getCssClass(rating)%>"><%= Ratings.getLabel(rating)%></span>
<% } %>		
	</p>

	<p>
		Die Telefonnummer ☎ <code><%= info.getPhone() %></code> is eine mehrfach berichtete Quelle von <a href="<%=request.getContextPath() %>/status.jsp">unerwünschten 
		Telefonanrufen</a>.
	</p>

<%
		}
	}
%>

<% if (!android) { %>
	<p>
		Wenn Du Dich von Anrufen von dieser Rufnummer belästigt fühlst, <a href="<%= request.getContextPath()%>/setup.jsp">installiere PhoneBlock</a> 
		und trage  die Nummer in Deiner Fritz!Box in die Blocklist ein oder gibt unten eine Bewertung für diese Nummer ab. So schützt Du Dich und andere
		vor weiterem Telefonterror von dieser Nummer.
	</p>
<% } else {%>
	<p>
		Wenn Du Dich von dieser Rufnummer belästigt fühlst, <a href="<%= request.getContextPath()%>/setup-android/">installiere PhoneBlock</a> 
		und gibt unten eine Bewertung für diese Nummer ab.
	</p>
<% } %>

	<h2>Bewertung</h2>
	
<% if (thanks) { %>
	<div id="thanks" class="notification is-info">
	  Danke für Deine Bewertung, Du hilfst damit anderen, die ebenfalls angerufen werden. 
	</div>
<% } else {%>
	<p>
	Du wurdest von ☎ <code><%= info.getPhone()%></code> angerufen? Sag anderen, was sie von dieser Nummer zu erwarten haben:
	</p>
	
	<p>
	<form action="<%=request.getContextPath()%>/rating" method="post">
		<input type="hidden" name="phone" value="<%= info.getPhone() %>"/>
		<div class="buttons">
		  	<button name="rating" value="<%=Rating.A_LEGITIMATE%>" type="submit" class="button is-rounded <%=Ratings.getCssClass(Rating.A_LEGITIMATE)%>">
			    <span class="icon">
					<i class="fa-solid fa-check"></i>
			    </span>
				<span>Seriös</span>			
	  		</button>
		  	<button name="rating" value="<%=Rating.B_MISSED%>" type="submit" class="button is-rounded <%=Ratings.getCssClass(Rating.B_MISSED)%>">
			    <span class="icon">
					<i class="fa-solid fa-circle-question"></i>
			    </span>
		  		<span>Anruf verpasst</span>
	  		</button>
		  	<button name="rating" value="<%=Rating.C_PING%>" type="submit" class="button is-rounded <%=Ratings.getCssClass(Rating.C_PING)%>">
			    <span class="icon">
					<i class="fa-solid fa-table-tennis-paddle-ball"></i>
			    </span>
		  		<span>Direkt aufgelegt</span>
	  		</button>
			<button name="rating" value="<%=Rating.D_POLL%>" type="submit" class="button is-rounded <%=Ratings.getCssClass(Rating.D_POLL)%>">
			    <span class="icon">
					<i class="fa-solid fa-person-chalkboard"></i>
			    </span>
				<span>Umfrage</span>
			</button>
			<button name="rating" value="<%=Rating.E_ADVERTISING%>" type="submit" class="button is-rounded <%=Ratings.getCssClass(Rating.E_ADVERTISING)%>">
			    <span class="icon">
					<i class="fa-solid fa-ban"></i>
			    </span>
				<span>Werbung</span>
			</button>
			<button name="rating" value="<%=Rating.F_GAMBLE%>" type="submit" class="button is-rounded <%=Ratings.getCssClass(Rating.F_GAMBLE)%>">
			    <span class="icon">
					<i class="fa-solid fa-dice"></i>
			    </span>
				<span>Gewinnspiel</span>
			</button>
			<button name="rating" value="<%=Rating.G_FRAUD%>" type="submit" class="button is-rounded <%=Ratings.getCssClass(Rating.G_FRAUD)%>">
			    <span class="icon">
					<i class="fa-solid fa-bomb"></i>
			    </span>
				<span>Betrug/Inkasso</span>
			</button>
		</div>
	</form>
	</p>
<% } %>

<div class="columns">
	<div class="column is-half">
<% if (!ratings.isEmpty()) { %>
	<canvas id="ratings" width="400" height="100" aria-label="Anzahl Bewertungen" role="img"></canvas>
	<script type="text/javascript">
	new Chart(document.getElementById('ratings').getContext('2d'), {
	    type: 'bar',
	    data: {
	        labels: [
	        	<%
	        	{
		        	boolean first = true;
		        	for (Rating r : Rating.values()) {
		        		if (android && ratings.getOrDefault(r, Integer.valueOf(0)) == 0) {
							continue;
		        		}
		        		if (first) {
		        			first = false;
		        		} else {
		        			out.write(',');
		        		}
		        		out.write('\'');
		        		out.write(Ratings.getLabel(r));
		        		out.write('\'');
		        	}
	        	}
				%>
        	],
	        datasets: [{
	            label: 'Anzahl Bewertungen',
	            data: [
		        	<%
		        	{
			        	boolean first = true;
			        	for (Rating r : Rating.values()) {
			        		if (android && ratings.getOrDefault(r, Integer.valueOf(0)) == 0) {
								continue;
			        		}
			        		if (first) {
			        			first = false;
			        		} else {
			        			out.write(',');
			        		}
			        		out.write(Integer.toString(ratings.getOrDefault(r, 0)));
			        	}
		        	}
					%>
            	],
	            backgroundColor: [
		        	<%
		        	{
			        	boolean first = true;
			        	for (Rating r : Rating.values()) {
			        		if (android && ratings.getOrDefault(r, Integer.valueOf(0)) == 0) {
								continue;
			        		}
			        		if (first) {
			        			first = false;
			        		} else {
			        			out.write(',');
			        		}
			        		out.write("'rgba(");
			        		out.write(Ratings.getRGB(r));
			        		out.write(", 0.2)'");
			        	}
		        	}
					%>
	            ],
	            borderColor: [
		        	<%
		        	{
			        	boolean first = true;
			        	for (Rating r : Rating.values()) {
			        		if (android && ratings.getOrDefault(r, Integer.valueOf(0)) == 0) {
								continue;
			        		}
			        		if (first) {
			        			first = false;
			        		} else {
			        			out.write(',');
			        		}
			        		out.write("'rgba(");
			        		out.write(Ratings.getRGB(r));
			        		out.write(", 1)'");
			        	}
		        	}
					%>
	            ],
	            borderWidth: 1
	        }]
	    },
	    options: {
	        scales: {
	            y: {
	                beginAtZero: true
	            }
	        }
	    }
	});
	</script>
<% } else { %>
	<p>
		Es gibt bisher noch keine Bewertungen, sei der erste, der eine seine Einschätzung teilt!
	</p>
<% } %>
	</div>

	<div class="column is-half">
<% if (!searches.isEmpty()) { %>
	<canvas id="searches" width="400" height="100" aria-label="Suchanfragen in der letzten Woche" role="img"></canvas>
	<script type="text/javascript">
	new Chart(document.getElementById('searches').getContext('2d'), {
		type: 'line',
	    data: {
	        labels: [
	        	<%
	        	SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.");
	        	Calendar date = new GregorianCalendar();
	        	date.add(Calendar.DAY_OF_MONTH, -(searches.size() - 1));
	        	{
		        	boolean first = true;
		        	for (SearchInfo r : searches) {
		        		if (first) {
		        			first = false;
		        		} else {
		        			out.write(',');
		        		}
		        		out.write('\'');
		        		out.write(fmt.format(date.getTime()));
		        		out.write('\'');
		        		
		        		date.add(Calendar.DAY_OF_MONTH, 1);
		        	}
	        	}
				%>
        	],
	        datasets: [{
	            label: 'Suchanfragen in der letzten Woche',
	            data: [
		        	<%
		        	{
			        	boolean first = true;
			        	for (SearchInfo r : searches) {
			        		if (first) {
			        			first = false;
			        		} else {
			        			out.write(',');
			        		}
			        		out.write(Integer.toString(r.getSearchesToday()));
			        	}
		        	}
					%>
            	],
            	fill: false,
                borderColor: 'rgb(75, 192, 192)',
                tension: 0.1
        	}]
	    },
	    options: {
	        scales: {
	            y: {
	                beginAtZero: true
	            }
	        }
	    }
	});
	</script>
<% } else {%>
	<p>Es gibt keine Suchanfragen für diese Nummer.</p>
<% } %>
	</div>
</div>

<% if (android) { %>

	<h2>Keine Lust mehr nach Telefonnummern zu googeln?</h2>
	<p>
		<a href="<%=request.getContextPath() %>/setup-android/">Installiere PhoneBlock auf Deinem Android-Mobiltelefon</a> 
		und du weißt sofort, ob es sich lohnt den Anruf anzunehmen oder eine Nummer zurückzurufen. 
	</p>	
<% } %>

	<h2>Details</h2>
<%
	DateFormat format = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.GERMAN);
%>			
	<ul>
		<li>Alternative Schreibweisen: <%if (analysis.getShortcut() != null) {%><code><%= analysis.getShortcut() %></code>, <%}%><code><%= analysis.getPlus() %></code>, <code><%= analysis.getZeroZero() %></code></li>
		<li>Land: <%= analysis.getCountry() %> (<code><%= analysis.getCountryCode() %></code>)</li>

		<%if (analysis.getCity() != null) { %>		
		<li>Stadt: <%= analysis.getCity() %> (<code><%= analysis.getCityCode() %></code>)</li>
		<%}%>

<%
		if (complaints > 0) {
%>	
		<li>Anzahl Beschwerden: <%= complaints %></li>
		<li>Letzte Beschwerde vom: <%= format.format(new Date(info.getLastUpdate())) %></li>

<%
			long dateAdded = info.getDateAdded();
			if (dateAdded > 0) {
%>
		<li>Nummer aktiv seit: <%= format.format(new Date(dateAdded)) %></li>
<%			
			}
		} 
%>
	</ul>

</div>
</section>


<section class="section">

<% if (android) { %>
<div class="tile is-ancestor">
	<div class="tile is-parent is-6">
		<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/setup-android/">
			<p class="title">PhoneBlock für Android</p>
			<p class="subtitle">Noch nicht installiert? Dann los!</p>
		</a>
	</div>

	<div class="tile is-parent is-6 ">
		<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/setup.jsp">
			<p class="title">PhoneBlock für Fritz!Box</p>
			<p class="subtitle">Werbeanrufe auf dem Festnetz ausschalten!</p>
		</a>
	</div>
</div>

<% } else { %>

<%
		if (info.getVotes() < DB.MIN_VOTES) {
%>

<div class="tile is-ancestor">
	<div class="tile is-parent is-6 ">
		<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/block.jsp">
			<p class="title">Rufnummer sperren</p>
			<p class="subtitle">Melde neue Quelle von Telefonterror!</p>
		</a>
	</div>

	<div class="tile is-parent is-6">
		<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/signup.jsp">
			<p class="title">PhoneBlock installieren</p>
			<p class="subtitle">Noch nicht installiert? Dann los!</p>
		</a>
	</div>
</div>

<%
		} else {
%>

<div class="tile is-ancestor">
	<div class="tile is-parent is-6">
		<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/signup.jsp">
			<p class="title">PhoneBlock installieren</p>
			<p class="subtitle">Account erstellen und einrichten!</p>
		</a>
	</div>

	<div class="tile is-parent is-6 ">
		<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/setup.jsp">
			<p class="title">Installation überprüfen</p>
			<p class="subtitle">Check die Installationsanleitung!</p>
		</a>
	</div>
</div>

<% 		} %>
<% } %>

</section>

<jsp:include page="footer.jspf"></jsp:include>
</body>
</html>