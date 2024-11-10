<!DOCTYPE html>
<%@page import="de.haumacher.phoneblock.app.UIProperties"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false"%>
<html>
<head>
<jsp:include page="../head-content.jspf"></jsp:include>
<link rel="stylesheet" href="<%=request.getContextPath() %><%=UIProperties.SWAGGER_PATH %>/swagger-ui.css" />
</head>

<body>
<jsp:include page="../header.jspf"></jsp:include>

<section id="swagger-ui" class="section">
</section>

<script src="<%=request.getContextPath() %><%=UIProperties.SWAGGER_PATH %>/swagger-ui-bundle.js"></script>
<script type="text/javascript" src="api.js"></script>
<input type="hidden" id="context-path" value="<%=request.getContextPath()%>">
<jsp:include page="../footer.jspf"></jsp:include>
</body>
</html>