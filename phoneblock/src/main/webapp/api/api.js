window.onload = () => {
    const contextPath = document.getElementById("context-path").value;
    window.ui = SwaggerUIBundle({
        url: contextPath + '/api/phoneblock.json',
        dom_id: '#swagger-ui',
    });
};