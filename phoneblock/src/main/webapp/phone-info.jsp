<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.SearchServlet"%>
<%@page import="de.haumacher.phoneblock.app.api.model.PhoneInfo"%>
<%@page import="de.haumacher.phoneblock.app.LoginServlet"%>
<%@page import="de.haumacher.phoneblock.app.UIProperties"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.app.api.CommentVoteServlet"%>
<%@page import="de.haumacher.phoneblock.app.ExternalLinkServlet"%>
<%@page import="de.haumacher.phoneblock.app.api.model.UserComment"%>
<%@page import="java.util.GregorianCalendar"%>
<%@page import="java.util.Calendar"%>
<%@page import="java.util.ArrayList"%>
<%@page import="de.haumacher.phoneblock.app.api.model.SearchInfo"%>
<%@page import="java.util.Map"%>
<%@page import="de.haumacher.phoneblock.app.api.model.RatingInfo"%>
<%@page import="de.haumacher.phoneblock.db.Ratings"%>
<%@page import="de.haumacher.phoneblock.app.api.model.Rating"%>
<%@page import="de.haumacher.phoneblock.app.api.model.PhoneNumer"%>
<%@page import="java.util.Date"%>
<%@page import="java.text.DateFormat"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Locale"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="de.haumacher.phoneblock.db.DB"%>
<%@page import="de.haumacher.phoneblock.db.DBService"%>
<%@page import="de.haumacher.phoneblock.db.Status"%>
<%@page import="de.haumacher.phoneblock.db.Statistics"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
	PhoneNumer analysis = (PhoneNumer) request.getAttribute(SearchServlet.NUMBER_ATTR);
	PhoneInfo info = (PhoneInfo) request.getAttribute(SearchServlet.INFO_ATTR);
	Rating rating = (Rating) request.getAttribute(SearchServlet.RATING_ATTR);
	List<String> relatedNumbers = (List<String>) request.getAttribute(SearchServlet.RELATED_NUMBERS_ATTR);
	Map<Rating, Integer> ratings = (Map<Rating, Integer>) request.getAttribute(SearchServlet.RATINGS_ATTR);
	List<Integer> searches = (List<Integer>) request.getAttribute(SearchServlet.SEARCHES_ATTR);
	
	boolean thanks = request.getAttribute(SearchServlet.THANKS_ATTR) != null;
	
	List<UserComment> comments = (List<UserComment>) request.getAttribute(SearchServlet.COMMENTS_ATTR);
	String summary = (String) request.getAttribute(SearchServlet.SUMMARY_ATTR);
	String defaultSummary = (String) request.getAttribute(SearchServlet.DEFAULT_SUMMARY_ATTR);
	
	String prev = (String) request.getAttribute(SearchServlet.PREV_ATTR);
	String next = (String) request.getAttribute(SearchServlet.NEXT_ATTR);

	DateFormat format = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.GERMAN);
	DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMAN);
%>			
<head>
<jsp:include page="head-content.jspf"></jsp:include>

<% if (!ratings.isEmpty() || !searches.isEmpty()) { %>
<script type="text/javascript" src="<%=request.getContextPath() %><%=UIProperties.CHARTJS_PATH %>/dist/chart.umd.js"></script>
<% } %>

<link rel="stylesheet" href="<%= request.getContextPath() %>/phone-info.css">
</head>

<body>
<jsp:include page="header.jspf"></jsp:include>

<section class="section">
<div class="content">
	<h1>Rufnummer ☎ <%= info.getPhone()%></h1>

<%
	String categoryClass;
	if (info.getVotes() <= 0) {
		categoryClass = "is-success";
%>
	<p>
<%
		if (info.isWhiteListed()) {
%>
			<span class="tag is-info <%= categoryClass%>"><i class="fa-solid fa-star"></i>&nbsp;Auf der weißen Liste</span>
<%
		} else if (info.getVotesWildcard() > 0) {
			categoryClass = "is-warning";
%>
			<span class="tag is-info <%= categoryClass%>">Nummernblock mit Spamverdacht</span>
<% if (rating != Rating.B_MISSED && rating != Rating.A_LEGITIMATE) { %>
			<span class="tag is-info <%= Ratings.getCssClass(rating)%>"><%= Ratings.getLabel(rating)%></span>
<% } %>
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
    <p>Information zur Rufnummer <%= analysis.getPlus() %></p>
  </div>
  <div class="message-body">

<% if (summary != null) { %>
	<%-- Note: No additional quoting, is already quoted. --%>
	<p><%= summary %></p>
<% } %>

	<%-- Note: No additional quoting, is already quoted. --%>
	<p><%= defaultSummary %></p>
  
	<ul>
		<li>Alternative Schreibweisen: <%if (analysis.getShortcut() != null) {%><code><%= analysis.getShortcut() %></code>, <%}%><code><%= analysis.getPlus() %></code>, <code><%= analysis.getZeroZero() %></code></li>
		<li>Land: <%= analysis.getCountry() %> (<code><%= analysis.getCountryCode() %></code>)</li>

		<%if (analysis.getCity() != null) { %>		
		<li>Stadt: <%= analysis.getCity() %> (<code><%= analysis.getCityCode() %></code>)</li>
		<%}%>

<% if (info.getVotes() > 0) { %>	
		<li>Stimmen für Sperrung: <%= info.getVotes() %></li>
<% } %>	
		
<% if (info.getVotesWildcard() > info.getVotes()) { %>
		<li>Stimmen für Sperrung des Nummernblocks: <%= info.getVotesWildcard() %></li>
<%} %>	
		
<% if (info.getVotes() > 0 || info.getVotesWildcard() > 0) { %>	
		<li>Letzte Beschwerde vom: <%= format.format(new Date(info.getLastUpdate())) %></li>

<%
	long dateAdded = info.getDateAdded();
	if (dateAdded > 0) {
%>
		<li>Nummer aktiv seit: <%= format.format(new Date(dateAdded)) %></li>
<% 	} %>
<% } %>
	</ul>
  
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
	<blockquote class="related-numbers">
<% for (String related : relatedNumbers) { %>
		<span><a href="<%= request.getContextPath()%>/nums/<%= related%>" data-onclick="showNumber">☎ <%= related %></a></span>
<% } %>	
	</blockquote>

<% } %>	

	<h2>Schreib eine Bewertung für <%= info.getPhone() %></h2>
	
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
		Damit Deine Bewertung sofort einen Einfluss auf Deine Blocklist hat, <a href="<%= request.getContextPath() %>/login.jsp<%= LoginServlet.locationParamFirst(request) %>">melde Dich vorher an</a>!
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
	<%
		StringBuilder labels = new StringBuilder();
		StringBuilder data = new StringBuilder();
		StringBuilder backgroundColor = new StringBuilder();
		StringBuilder borderColor = new StringBuilder();

		boolean first = true;
		for (Rating r : Rating.values()) {
			if (r == Rating.B_MISSED) {
				continue;
			}
			if (first) {
				first = false;
			} else {
				labels.append(',');
				data.append(',');
				backgroundColor.append('|');
				borderColor.append('|');
			}
			labels.append(Ratings.getLabel(r));
			data.append(ratings.getOrDefault(r, 0));
			String rgb = Ratings.getRGB(r);
			backgroundColor.append("rgba(").append(rgb).append(", 0.2)");
			borderColor.append("rgba(").append(rgb).append(", 1)");
		}
	%>
	<div id="ratings-data" ratings-labels="<%=labels.toString()%>" ratings-dataset="<%=data.toString()%>" ratings-backgroundColor="<%=backgroundColor.toString()%>" ratings-borderColor="<%=borderColor.toString()%>">
	</div>
	<canvas id="ratings" width="400" height="200" aria-label="Anzahl Bewertungen" role="img"></canvas>
	<script type="text/javascript" src="<%= request.getContextPath() %>/phone-info-ratings.js"></script>
<% } else { %>
	<p>
		Es gibt bisher noch keine Bewertungen, sei der erste, der eine seine Einschätzung teilt!
	</p>
<% } %>
	</div>

	<div class="column is-half">
<% if (!searches.isEmpty()) { %>
	<%
		SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.");
		Calendar date = new GregorianCalendar();
		date.add(Calendar.DAY_OF_MONTH, -(searches.size() - 1));

		StringBuilder labels = new StringBuilder();
		StringBuilder data = new StringBuilder();

		boolean first = true;

		for (Integer r : searches) {
			if (first) {
				first = false;
			} else {
				labels.append(',');
				data.append(',');
			}
			labels.append(fmt.format(date.getTime()));
			date.add(Calendar.DAY_OF_MONTH, 1);
			data.append(r);
		}
	%>
	<div id="searches-data" searches-labels="<%=labels.toString()%>" searches-dataset="<%=data.toString()%>">
	</div>
	<canvas id="searches" width="400" height="200" aria-label="Suchanfragen in der letzten Woche" role="img"></canvas>
	<script type="text/javascript" src="<%= request.getContextPath() %>/phone-info-searches.js"></script>
<% } else {%>
	<p>Es gibt keine Suchanfragen für diese Nummer.</p>
<% } %>
	</div>
</div>

<% if (!comments.isEmpty()) { %>
	<h2>Kommentare zu <%= analysis.getPlus() %></h2>
	
	<% 
	int commentId = 1;
	String votePath = request.getContextPath() + CommentVoteServlet.PATH; 
	%>
	<% for (UserComment comment : comments.subList(0, Math.min(10, comments.size()))) { %>
	<input type="hidden" id="votePath" value="<%=votePath%>">
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
		  <strong>☎ <%= comment.getPhone()%></strong>
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
          <a class="level-item thumbs-up commentVote" aria-label="Guter Hinweis" title="Guter Hinweis!" href="#" data-comment-id="<%=comment.getId()%>" data-vote-up-id="<%=upId%>" data-vote-down-id="<%=downId%>">
            <span class="icon">
              <i class="fa-solid fa-thumbs-up"></i>
            </span>
            &nbsp;<span id="<%=upId%>"><%=up%></span>
          </a>
          <a class="level-item thumbs-down commentVote" aria-label="Unsinn" title="Unsinn!" href="#" data-comment-id="<%=comment.getId()%>" data-vote-up-id="<%=upId%>" data-vote-down-id="<%=downId%>">
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

<% if (android) { %>

	<h2>Keine Lust mehr nach Telefonnummern zu googeln?</h2>
	<p>
		<a href="<%=request.getContextPath() %>/setup-android/">Installiere PhoneBlock auf Deinem Android-Mobiltelefon</a> 
		und du weißt sofort, ob es sich lohnt den Anruf anzunehmen oder eine Nummer zurückzurufen. 
	</p>	
<% } %>
	
	<nav class="pagination is-centered" role="navigation" aria-label="pagination">
	<% if (prev != null) { %>
		<a class="pagination-previous" data-onclick="showNumber" href="<%= request.getContextPath()%>/nums/<%= prev%>">Vorherige Nummer</a>
	<% } %>
	<% if (next != null) { %>
		<a class="pagination-next" data-onclick="showNumber" href="<%= request.getContextPath()%>/nums/<%= next%>">Nächste Nummer</a>
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
		<a class="tile is-child notification is-info" href="<%=request.getContextPath() %>/login.jsp<%= LoginServlet.locationParamFirst("/setup.jsp") %>">
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
		<a class="tile is-child notification is-primary" href="<%=request.getContextPath() %>/login.jsp<%= LoginServlet.locationParamFirst("/setup.jsp") %>">
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