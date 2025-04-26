const tokenElement = document.getElementById("token");
const token = tokenElement.textContent;

// Post the token to the native JavaScript channel on the client.
if (TokenResult) {
	TokenResult.postMessage(token);
}