<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.ab.CreateABServlet"%>
<%@page import="de.haumacher.phoneblock.random.SecureRandomService"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"	pageEncoding="UTF-8" session="false"%>
<%@page import="de.haumacher.phoneblock.util.JspUtil"%>
<html>
<%
request.setAttribute("title", "PhoneBlock-Anrufbeantworter");

HttpSession session = request.getSession();
String username = (String) session.getAttribute("ab-username");
String passwd = (String) session.getAttribute("ab-passwd");
if (username == null) {
    username = "ab-" + Math.abs(SecureRandomService.getInstance().getRnd().nextLong());
    passwd = Long.toHexString(Math.abs(SecureRandomService.getInstance().getRnd().nextLong()));
    
    session.setAttribute("ab-username", username);
    session.setAttribute("ab-passwd", passwd);
}
%>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
</head>

<body>
<jsp:include page="../header.jspf"></jsp:include>

<section class="section">
	<div class="content">
		<h1>PhoneBlock-Anrufbeantworter</h1>
		
		<p>
			Der PhoneBlock-Anrufbeantworter wird als "IP-Telefon mit Anrufbeantworter" an der Fritz!Box (oder einem 
			anderen Internet-Router) eingerichtet. 
		</p>
	</div>
	
<form action="<%= request.getContextPath() + CreateABServlet.PATH %>" method="post">

<div class="field is-horizontal">
  <div class="field-label is-normal">
    <label class="label">Host-Name</label>
  </div>
  <div class="field-body">
    <div class="field">
      <p class="control is-expanded has-icons-left">
        <input class="input" type="text" placeholder="xyzabcdef.myfritz.net" name="hostname">
        <span class="icon is-small is-left">
          <i class="fas fa-server"></i>
        </span>
      </p>
    </div>
  </div>
</div>

<div class="field is-horizontal">
  <div class="field-label is-normal">
    <label class="label">Benutzername</label>
  </div>
  <div class="field-body">
    <div class="field">
      <p class="control is-expanded">
      	<span class="input"><%=JspUtil.quote(username) %></span>
      </p>
    </div>
  </div>
</div>
		
<div class="field is-horizontal">
  <div class="field-label is-normal">
    <label class="label">Kennwort</label>
  </div>
  <div class="field-body">
    <div class="field">
      <p class="control is-expanded">
        <span class="input"><%=JspUtil.quote(passwd) %></span>
      </p>
    </div>
  </div>
</div>

<div class="field is-grouped">
  <div class="control">
    <button class="button is-link" type="submit">Einrichten</button>
  </div>
  
  <div class="control">
    <button class="button is-link is-light">Abbrechen</button>
  </div>
</div>

</form>
		
</section>

<jsp:include page="../footer.jspf"></jsp:include>
</body>
</html>