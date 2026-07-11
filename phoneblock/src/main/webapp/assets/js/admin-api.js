window.onload = () => {
    const specUrl = document.getElementById("openapi-url").value;
    window.ui = SwaggerUIBundle({
        url: specUrl,
        dom_id: '#swagger-ui',
        // Keep the entered bearer token across reloads for convenience.
        persistAuthorization: true,
    });
};
