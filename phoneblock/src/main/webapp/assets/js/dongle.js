/*
 * Dongle page: the browser installer needs Web Serial (Chrome, Edge, Opera on
 * the desktop); without it, show the hint instead.
 */
(function() {
	if (!('serial' in navigator)) {
		var box = document.getElementById('dongle-install-unsupported');
		if (box) { box.classList.remove('pb-hidden'); }
	}
})();
