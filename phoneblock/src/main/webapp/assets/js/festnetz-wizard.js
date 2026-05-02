// Decision tree for the /festnetz page: three questions, then unhide the
// matching `.wizard-result` block. The result texts live in the template so
// the auto-translate pipeline can localise them; this script only flips
// visibility.
(function () {
	var wizard = document.getElementById('festnetzWizard');
	if (!wizard) return;

	var steps = {};
	wizard.querySelectorAll('[data-step]').forEach(function (el) {
		steps[el.getAttribute('data-step')] = el;
	});

	var results = {};
	wizard.querySelectorAll('[data-result]').forEach(function (el) {
		results[el.getAttribute('data-result')] = el;
	});

	var restart = wizard.querySelector('.wizard-restart');

	var state = {};

	function show(el) { if (el) el.hidden = false; }
	function hide(el) { if (el) el.hidden = true; }

	function reset() {
		state = {};
		Object.values(steps).forEach(hide);
		Object.values(results).forEach(hide);
		hide(restart);
		show(steps.router);
	}

	function showStep(name) {
		Object.values(steps).forEach(hide);
		show(steps[name]);
	}

	function showResult(name) {
		Object.values(steps).forEach(hide);
		Object.values(results).forEach(hide);
		show(results[name]);
		show(restart);
	}

	function pickFritzboxResult() {
		var modern = state.age === 'modern';
		var own = state.ownership === 'own';
		if (own && modern) return 'all-three';
		if (own && !modern) return 'dongle-cloudab';
		if (!own && modern) return 'dongle-blockliste';
		return 'dongle-only';
	}

	function pickProviderResult() {
		switch (state.provider) {
			case 'telekom':   return 'dongle-telekom';
			case 'vodafone':  return 'vodafone-deadend';
			default:          return 'dongle-other-isp';
		}
	}

	wizard.addEventListener('click', function (event) {
		var btn = event.target.closest('button.wizard-btn');
		if (btn) {
			var route = btn.getAttribute('data-route');
			var step = btn.closest('[data-step]').getAttribute('data-step');
			handleAnswer(step, route);
			return;
		}
		var restartLink = event.target.closest('.wizard-restart a');
		if (restartLink) {
			event.preventDefault();
			reset();
		}
	});

	function handleAnswer(step, route) {
		switch (step) {
			case 'router':
				state.router = route;
				if (route === 'fritzbox') showStep('ownership');
				else showStep('provider');
				return;
			case 'ownership':
				state.ownership = route;
				showStep('age');
				return;
			case 'age':
				state.age = route;
				showResult(pickFritzboxResult());
				return;
			case 'provider':
				state.provider = route;
				showResult(pickProviderResult());
				return;
		}
	}

	reset();
})();
