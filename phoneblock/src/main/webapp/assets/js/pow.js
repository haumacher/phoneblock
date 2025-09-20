async function digestMessage(message) {
  const encoder = new TextEncoder();
  const data = encoder.encode(message);
  const hash = await window.crypto.subtle.digest("SHA-256", data);
  return hash;
}

async function prove(challenge, difficulty = 2) {
	try {
		let solution = 1;
		while (true) {
		    const hashBuffer = await digestMessage(challenge + solution);
			const hashArray = Array.from(new Uint8Array(hashBuffer));
			if (check(hashArray, difficulty)) {
				return solution;
			}
			solution++;
		}
	} catch (ex) {
		return 0;
	}
}

function check(hashArray, difficulty) {
	for (var n = 0; n < difficulty; n++) {
		if (hashArray[n] != 0) {
			return false;
		}
	}
	return true;
}

window.addEventListener("DOMContentLoaded", () => {
	(async () => {
		// Parse current URL
		const url = new URL(window.location.href);
		const params = url.searchParams;
		
		// Get challenge parameter
		const challenge = params.get("challenge");
		
		if (challenge) {
			// Call prove() with the challenge
			const solution = await prove(challenge);
			
			// Add solution param
			params.delete("challenge");
			params.set("solution", solution);
			
			// Redirect to updated URL
			window.location.replace(url.toString());
		}
	})();
});
