/*
 * Browser-side glue for the dongle install-page pairing flow.
 *
 *   1. Mint a per-session 16-byte secret (sessionStorage so a back/forward
 *      navigation in the tab keeps it).
 *   2. Fetch the upstream stable manifest from the CDN, append a per-install
 *      pairing.bin part pointing at /dongle/pairing.bin?secret=<hex> on
 *      the same webapp (so test installs hit /pb-test, prod hits /phoneblock
 *      automatically), and feed the patched manifest to esp-web-tools as a
 *      Blob URL.
 *   3. Once the dongle has booted on its new WiFi and POSTed back to
 *      /api/dongle/register, polling /api/dongle/lookup returns its LAN IP
 *      and we update the step-7 link plus a success banner so the user can
 *      jump straight to the right address.
 *
 * Every failure mode falls through silently to the existing manual flow
 * (http://answerbot/ link plus router-IP fallback paragraph) — pairing is
 * a UX shortcut, not a hard requirement.
 */
(function() {
	'use strict';

	if (!window.crypto || !crypto.getRandomValues) return;
	if (!window.fetch || !window.Blob || !URL.createObjectURL) return;

	var SECRET_KEY      = 'pairingSecret';
	var UPSTREAM        = 'https://cdn.phoneblock.net/dongle/firmware/stable/manifest.json';
	var PAIRING_OFFSET  = 0x12000;
	var POLL_INTERVAL   = 3000;
	// Long enough to cover unplug → replug → WPS → DHCP → register on a
	// slow router (~5 minutes is comfortable; the server-side TTL is 30 min,
	// so we never lookup past the entry's lifetime).
	var POLL_MAX_TRIES  = 100;

	var pairingSecret = sessionStorage.getItem(SECRET_KEY);
	var reusedSecret  = pairingSecret !== null;
	if (!pairingSecret) {
		var bytes = crypto.getRandomValues(new Uint8Array(16));
		pairingSecret = Array.prototype.map.call(bytes, function(b) {
			return ('0' + b.toString(16)).slice(-2);
		}).join('');
		sessionStorage.setItem(SECRET_KEY, pairingSecret);
	}

	// Path of the current webapp (e.g. "/phoneblock" or "/pb-test"), so
	// the pairing-bin URL and the lookup URL hit the SAME backend the
	// install page came from. Falls back to /phoneblock if we can't pick
	// the prefix off the URL — that matches production.
	function contextPath() {
		var m = location.pathname.match(/^(\/[^/]+)\/dongle-install/);
		return m ? m[1] : '/phoneblock';
	}

	var pairingBinUrl =
		location.origin + contextPath() + '/dongle/pairing.bin?secret=' + pairingSecret;
	var lookupUrl =
		location.origin + contextPath() + '/api/dongle/lookup?secret=' + pairingSecret;

	function patchManifest() {
		fetch(UPSTREAM, { credentials: 'omit', cache: 'no-store' })
			.then(function(r) {
				if (!r.ok) throw new Error('upstream HTTP ' + r.status);
				return r.json();
			})
			.then(function(manifest) {
				var build = (manifest.builds || [])[0];
				if (!build || !Array.isArray(build.parts)) {
					throw new Error('manifest has no builds[0].parts');
				}
				build.parts.push({ path: pairingBinUrl, offset: PAIRING_OFFSET });
				var blob = new Blob([JSON.stringify(manifest)],
					{ type: 'application/json' });
				var blobUrl = URL.createObjectURL(blob);
				var installer = document.querySelector('esp-web-install-button');
				if (installer) {
					installer.setAttribute('manifest', blobUrl);
				}
			})
			.catch(function(err) {
				// Static manifest attribute on the element is the
				// upstream URL already, so doing nothing here keeps
				// flashing functional — it just won't pair.
				console.warn('pairing manifest patch failed:', err);
			});
	}

	var pollTimer = null;
	var pollTries = 0;
	function startPolling() {
		if (pollTimer) return;
		pollTries = 0;
		pollOnce();
		pollTimer = setInterval(pollOnce, POLL_INTERVAL);
	}

	function pollOnce() {
		pollTries++;
		if (pollTries > POLL_MAX_TRIES) {
			clearInterval(pollTimer);
			pollTimer = null;
			return;
		}
		fetch(lookupUrl, { credentials: 'omit', cache: 'no-store' })
			.then(function(r) {
				if (r.status === 200) return r.json();
				return null;
			})
			.then(function(data) {
				if (data && data.lanIp) {
					clearInterval(pollTimer);
					pollTimer = null;
					onDongleLocated(data.lanIp);
				}
			})
			.catch(function() { /* network blip — keep polling */ });
	}

	function onDongleLocated(ip) {
		var url = 'http://' + ip + '/';

		var step7 = document.getElementById('dongle-step7-link');
		if (step7) {
			step7.href = url;
			step7.textContent = url;
		}

		var banner = document.getElementById('dongle-located-banner');
		var bannerLink = document.getElementById('dongle-located-link');
		if (banner && bannerLink) {
			bannerLink.href = url;
			bannerLink.textContent = url;
			banner.style.display = '';
		}
	}

	patchManifest();

	var installer = document.querySelector('esp-web-install-button');
	if (installer) {
		installer.addEventListener('state-changed', function(ev) {
			var state = ev && ev.detail && ev.detail.state;
			if (state === 'FINISHED') {
				startPolling();
			}
		});
	}

	// If the user reloaded the install page after a previous flash in
	// the same tab, the secret is still in sessionStorage and the dongle
	// may already be registered — start polling immediately so the page
	// catches up. For a fresh secret we wait for FINISHED.
	if (reusedSecret) {
		startPolling();
	}
})();
