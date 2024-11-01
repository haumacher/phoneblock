<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.UIProperties"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.app.api.CommentVoteServlet"%>
<%@page import="de.haumacher.phoneblock.app.ExternalLinkServlet"%>
<%@page import="de.haumacher.phoneblock.db.model.UserComment"%>
<%@page import="java.util.GregorianCalendar"%>
<%@page import="java.util.Calendar"%>
<%@page import="java.util.ArrayList"%>
<%@page import="de.haumacher.phoneblock.db.model.SearchInfo"%>
<%@page import="java.util.Map"%>
<%@page import="de.haumacher.phoneblock.db.model.RatingInfo"%>
<%@page import="de.haumacher.phoneblock.db.Ratings"%>
<%@page import="de.haumacher.phoneblock.db.model.Rating"%>
<%@page import="de.haumacher.phoneblock.db.model.PhoneNumer"%>
<%@page import="de.haumacher.phoneblock.analysis.NumberAnalyzer"%>
<%@page import="java.util.Date"%>
<%@page import="java.text.DateFormat"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Locale"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="de.haumacher.phoneblock.db.DB"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.db.model.SpamReport"%>
<%@page import="de.haumacher.phoneblock.db.Status"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	PhoneNumer analysis = (PhoneNumer) request.getAttribute("number");
	SpamReport info = (SpamReport) request.getAttribute("info");
	Rating rating = (Rating) request.getAttribute("rating");
	List<String> relatedNumbers = (List<String>) request.getAttribute("relatedNumbers");
	Map<Rating, Integer> ratings = (Map<Rating, Integer>) request.getAttribute("ratings");
	List<? extends SearchInfo> searches = (List<? extends SearchInfo>) request.getAttribute("searches");
	
	boolean thanks = request.getAttribute("thanks") != null;
	
	List<UserComment> comments = (List<UserComment>) request.getAttribute("comments");
	String summary = (String) request.getAttribute("summary");
	String defaultSummary = (String) request.getAttribute("defaultSummary");
	
	String prev = (String) request.getAttribute("prev");
	String next = (String) request.getAttribute("next");
%>
<head>
<jsp:include page="head-content.jspf"></jsp:include>

<% if (!ratings.isEmpty() || !searches.isEmpty()) { %>
<script type="text/javascript" src="<%=request.getContextPath() %><%=UIProperties.CHARTJS_PATH %>/dist/chart.umd.js"></script>
<% } %>

<style type="text/css">
	.box .fa-star {
		color: green;
	}
	
	.fa-triangle-exclamation {
		color: red;
	}
	
	.image.is-64x64 i {
		font-size: 64px;
	}
	
	.level-item.thumbs-up {
		color: green;
	}
	
	.level-item.thumbs-down {
		color: red;
	}
	
	.level-item {
		padding-right: 1rem;
	}
</style>
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
<div class="content">
	<h1>Bewertung von ☎ <%= info.getPhone()%></h1>

<%
	String categoryClass;
	if (info.getVotes() == 0) {
		categoryClass = "is-success";
%>
	<p>
<%
		if (info.isWhiteListed()) {
%>
			<span class="tag is-info <%= categoryClass%>"><i class="fa-solid fa-star"></i>&nbsp;Auf der weißen Liste</span>
<%
		} else {
%>
			<span class="tag is-info <%= categoryClass%>">Keine Beschwerden</span>
<%
		}
%>
	</p>
<%
	} else {
		if (info.getVotes() < DB.MIN_VOTES || info.isArchived()) {
			categoryClass = "is-warning";
%>
	<p>
		<span class="tag is-info <%= categoryClass%>">Beschwerde liegt vor</span>
<% if (rating != Rating.B_MISSED) { %>
		<span class="tag is-info <%= Ratings.getCssClass(rating)%>"><%= Ratings.getLabel(rating)%></span>
<% } %>		
	</p>
<%
		} else {
			categoryClass = "is-danger";
%>			
	<p>
		<span class="tag is-info <%= categoryClass%>">Blockiert</span>
<% if (rating != Rating.B_MISSED) { %>
		<span class="tag is-info <%= Ratings.getCssClass(rating)%>"><%= Ratings.getLabel(rating)%></span>
<% } %>		
	</p>
<%
		}
	}
%>

<article class="message <%= categoryClass%>">
  <div class="message-header">
    <p>Information zum Anrufer</p>
  </div>
  <div class="message-body">

<% if (summary != null) { %>
	<%-- Note: No additional quoting, is already quoted. --%>
	<p><%= summary %></p>
<% } %>

	<%-- Note: No additional quoting, is already quoted. --%>
	<p><%= defaultSummary %></p>
  
  </div>
</article>

	<p>
		Unerwünschte Telefonanrufe kannst Du <em>automatisch blockieren</em>, wenn Du PhoneBlock installierst: 
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
	
<% if (relatedNumbers.size() > 1) { %>

	<p>Die Nummer ☎ <%= info.getPhone()%> könnte zum selben Anschluss gehören wie die folgenden Nummern in der Datenbank:</p>
	<blockquote style="display: flex; flex-wrap: wrap; column-gap: 64px; row-gap: 0px;">
<% for (String related : relatedNumbers) { %>
		<span><a href="<%= request.getContextPath()%>/nums/<%= related%>" onclick="return showNumber('<%= related%>');">☎ <%= related %></a></span>
<% } %>	
	</blockquote>

<% } %>	
	
<%
	DateFormat format = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.GERMAN);
	DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMAN);
%>			

<% if (!comments.isEmpty()) { %>
	<h2>Meinungen von anderen</h2>
	
	<% 
	int commentId = 1;
	String votePath = request.getContextPath() + CommentVoteServlet.PATH; 
	%>
	<% for (UserComment comment : comments.subList(0, Math.min(10, comments.size()))) { %>

<div class="box">
  <article class="media">
    <div class="media-left">
      <figure class="image is-64x64">
		<% if (comment.getRating() == Rating.A_LEGITIMATE) { %>
			<i class="fa-solid fa-star"></i>
		<% } else { %>
			<i class="fa-solid fa-triangle-exclamation"></i>
		<% } %>
      </figure>
    </div>
    <div class="media-content">
      <div class="content">
        <p class="commentHeader">
		  <strong>☎ <%= info.getPhone()%></strong>
          <small>
          <% if (comment.getService() != null && !comment.getService().isEmpty()) { %>
          <a target="_blank" href="<%= request.getContextPath()%><%= ExternalLinkServlet.LINK_PREFIX%><%= comment.getService()%>/<%=  comment.getPhone()%>"><%= comment.getService()%></a>
		  <% } else { %>
		  <span>PhoneBlock</span>
		  <% } %>
		  </small>
		  <small><%= dateFormat.format(new Date(comment.getCreated())) %></small>
        </p>
        <p class="commentText">
          <% if (comment.getService() != null && comment.getComment().length() > 280) { %>
	          <%= JspUtil.quote(comment.getComment().substring(0, 277) + "...") %>
          <% } else { %>
	          <%= JspUtil.quote(comment.getComment()) %>
          <% } %>
        </p>
      </div>
            
      <%
      	String upId = "id" + commentId++;
      	String downId = "id" + commentId++;
      	int up = Math.max(0, comment.getUp() - comment.getDown());
      	int down = Math.max(0, comment.getDown() - comment.getUp());
      %>
      <nav class="level is-mobile">
        <div class="level-left">
          <a class="level-item thumbs-up" aria-label="Guter Hinweis" title="Guter Hinweis!" href="#" onclick="return commentVote('<%=votePath %>', '<%=comment.getId()%>', 1, '<%=upId%>', '<%=downId%>');">
            <span class="icon">
              <i class="fa-solid fa-thumbs-up"></i>
            </span>
            &nbsp;<span id="<%=upId%>"><%=up%></span>
          </a>
          <a class="level-item thumbs-down" aria-label="Unsinn" title="Unsinn!" href="#" onclick="return commentVote('<%=votePath %>', '<%=comment.getId()%>', -1, '<%=upId%>', '<%=downId%>');">
            <span class="icon">
              <i class="fa-solid fa-thumbs-down"></i>
            </span>&nbsp;<span id="<%=downId%>"><%=down%></span>
          </a>
        </div>
      </nav>
    </div>
  </article>
</div>
	
	<%} %>	
<% } %>
	
	<h2>Deine Bewertung</h2>
	
<% if (thanks) { %>
	<div id="thanks" class="notification is-info">
	  Danke für Deine Bewertung, Du hilfst damit anderen, die ebenfalls angerufen werden. 
	</div>
<% } else {%>
	<p>
		Du wurdest von ☎ <code><%= info.getPhone()%></code> angerufen? Sag anderen, was sie von ☎ <code><%= analysis.getPlus()%></code> zu erwarten haben.
		Wenn Du Dich von Anrufen von ☎ <code><%= info.getPhone()%></code> belästigt fühlst, <a href="<%= request.getContextPath()%>/setup.jsp">installiere PhoneBlock</a> 
		und gibt eine Bewertung für ☎ <code><%= analysis.getZeroZero()%></code> ab:
	</p>

	<p>
	<form action="<%=request.getContextPath()%>/rating" method="post" enctype="application/x-www-form-urlencoded" accept-charset="utf-8" spellcheck="true">
		<input type="hidden" name="phone" value="<%= info.getPhone() %>"/>
		
		<div class="buttons">
		  	<label class="button is-rounded <%=Ratings.getCssClass(Rating.A_LEGITIMATE)%>">
		  		<input type="radio" name="rating" value="<%=Rating.A_LEGITIMATE%>">
			    <span class="icon">
					<i class="fa-solid fa-check"></i>
			    </span>
				<span>Seriös</span>			
	  		</label>
	  		
		  	<label class="button is-rounded <%=Ratings.getCssClass(Rating.B_MISSED)%>">
		  		<input type="radio" name="rating" value="<%=Rating.B_MISSED%>" checked="checked">
			    <span class="icon">
					<i class="fa-solid fa-circle-question"></i>
			    </span>
		  		<span>Sonstiges</span>
	  		</label>
	  		
		  	<label class="button is-rounded <%=Ratings.getCssClass(Rating.C_PING)%>">
		  		<input type="radio" name="rating" value="<%=Rating.C_PING%>">
			    <span class="icon">
					<i class="fa-solid fa-table-tennis-paddle-ball"></i>
			    </span>
		  		<span>Direkt aufgelegt</span>
	  		</label>
			<label class="button is-rounded <%=Ratings.getCssClass(Rating.D_POLL)%>">
		  		<input type="radio" name="rating" value="<%=Rating.D_POLL%>">
			    <span class="icon">
					<i class="fa-solid fa-person-chalkboard"></i>
			    </span>
				<span>Umfrage</span>
			</label>
			<label class="button is-rounded <%=Ratings.getCssClass(Rating.E_ADVERTISING)%>">
		  		<input type="radio" name="rating" value="<%=Rating.E_ADVERTISING%>">
			    <span class="icon">
					<i class="fa-solid fa-ban"></i>
			    </span>
				<span>Werbung</span>
			</label>
			<label class="button is-rounded <%=Ratings.getCssClass(Rating.F_GAMBLE)%>">
		  		<input type="radio" name="rating" value="<%=Rating.F_GAMBLE%>">
			    <span class="icon">
					<i class="fa-solid fa-dice"></i>
			    </span>
				<span>Gewinnspiel</span>
			</label>
			<label class="button is-rounded <%=Ratings.getCssClass(Rating.G_FRAUD)%>">
		  		<input type="radio" name="rating" value="<%=Rating.G_FRAUD%>">
			    <span class="icon">
					<i class="fa-solid fa-bomb"></i>
			    </span>
				<span>Betrug/Inkasso</span>
			</label>
		</div>

		<p>			
		<textarea name="comment" class="textarea is-primary" placeholder="Dein Bericht - Keine Beleidigungen, keine Schimpfwörter!"></textarea>
		</p>
		
		<p>
		Damit Deine Bewertung sofort einen Einfluss auf Deine Blocklist hat, <a href="<%= request.getContextPath() %>/login.jsp">melde Dich vorher an</a>!
		</p>
		
		<div class="buttons">
			<button name="send" type="submit" class="button is-rounded is-primary">
			    <span class="icon">
					<i class="fas fa-paper-plane"></i>
			    </span>
				<span>Abschicken</span>
			</button>
		</div>
	</form>
	</p>
<% } %>

<div class="columns">
	<div class="column is-half">
<% if (!ratings.isEmpty()) { %>
	<canvas id="ratings" width="400" height="200" aria-label="Anzahl Bewertungen" role="img"></canvas>
	<script type="text/javascript">
	new Chart(document.getElementById('ratings').getContext('2d'), {
	    type: 'bar',
	    data: {
	        labels: [
	        	<%
	        	{
		        	boolean first = true;
		        	for (Rating r : Rating.values()) {
		        		if (r == Rating.B_MISSED) {
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
			        		if (r == Rating.B_MISSED) {
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
			        		if (r == Rating.B_MISSED) {
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
			        		if (r == Rating.B_MISSED) {
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
	<canvas id="searches" width="400" height="200" aria-label="Suchanfragen in der letzten Woche" role="img"></canvas>
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
		        	<%{
			        	boolean first = true;
			        	for (SearchInfo r : searches) {
			        		if (first) {
			        			first = false;
			        		} else {
			        			out.write(',');
			        		}
			        		out.write(Integer.toString(r.getCount()));
			        	}
		        	}%>
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
	<ul>
		<li>Alternative Schreibweisen: <%if (analysis.getShortcut() != null) {%><code><%= analysis.getShortcut() %></code>, <%}%><code><%= analysis.getPlus() %></code>, <code><%= analysis.getZeroZero() %></code></li>
		<li>Land: <%= analysis.getCountry() %> (<code><%= analysis.getCountryCode() %></code>)</li>

		<%if (analysis.getCity() != null) { %>		
		<li>Stadt: <%= analysis.getCity() %> (<code><%= analysis.getCityCode() %></code>)</li>
		<%}%>

<%
		if (info.getVotes() > 0) {
%>	
		<li>Stimmen für Sperrung: <%= info.getVotes() %></li>
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
	
	<nav class="pagination is-centered" role="navigation" aria-label="pagination">
	<% if (prev != null) { %>
		<a class="pagination-previous" href="<%= request.getContextPath()%>/nums/<%= prev%>" onclick="return showNumber('<%= prev%>');">Vorherige Nummer</a>
	<% } %>
	<% if (next != null) { %>
		<a class="pagination-next" href="<%= request.getContextPath()%>/nums/<%= next%>" onclick="return showNumber('<%= next%>');">Nächste Nummer</a>
	<% } %>
	</nav>
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