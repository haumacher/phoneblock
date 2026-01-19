/** Map of named onclick handlers that an be associated with elements through the `data-onclick` attribute. */
const commands = {
	"showNumber": function (event) {
		const href = this.href;
		const number = href.substring(href.lastIndexOf("/") + 1)
		event.preventDefault();
		displayNumber(number, true);
	}
};

/** Link onclick handlers to elements referencing them through the data-onclick attribute. */
document.addEventListener('DOMContentLoaded', () => {
	const elements = document.querySelectorAll('[data-onclick]');
	elements.forEach((element) => {
		const cmdId = element.getAttribute("data-onclick");
		const cmd = commands[cmdId];
		
		if (cmd != null) {
			element.addEventListener('click', cmd);
		}
	});
});

document.addEventListener('DOMContentLoaded', () => {
  // Get all "navbar-burger" elements
  const $navbarBurgers = Array.prototype.slice.call(document.querySelectorAll('.navbar-burger'), 0);

  // Check if there are any navbar burgers
  if ($navbarBurgers.length > 0) {

    // Add a click event on each of them
    $navbarBurgers.forEach( el => {
      el.addEventListener('click', () => {

        // Get the target from the "data-target" attribute
        const target = el.dataset.target;
        const $target = document.getElementById(target);

        // Toggle the "is-active" class on both the "navbar-burger" and the "navbar-menu"
        el.classList.toggle('is-active');
        $target.classList.toggle('is-active');

      });
    });
  }
  
	var contextPath = getContextPath();
	if (contextPath == "/pb-test") {
		document.body.classList.add("test-system");
	}
	
	// Initialize all input of date type.
	if ('bulmaCalendar' in window) {
		const calendars = bulmaCalendar.attach('[type="date"]', {
			dateFormat: "yyyy-MM-dd",
			type: "date",
			displayMode: "dialog"
		});
		
		// Loop on each calendar initialized
		calendars.forEach(calendar => {
			// Add listener to select event
			calendar.on('select', date => {
				
				console.log(date);
			});
		});	
	}
});

function displayNumber(number, link) {
  var location = document.location;
  var contextPath = getContextPath();
  var url = location.protocol + "//" + location.host + contextPath + "/nums/" + number + (link ? "?link=true" : "");
  location.assign(url);
}

function getContextPath() {
	var slashIndex = location.pathname.indexOf("/", 1);
	var contextPath = location.pathname.substring(0, slashIndex);

	return contextPath;
}

function showaddr(target) {
  var addr =
    "haui" + String.fromCharCode(64) + "haumacher" +
    String.fromCharCode(46) + "de";
  var link = document.createElement("a");
  link.setAttribute("href", "mailto:" + addr);
  link.appendChild(
    document.createTextNode(addr));
  target.parentNode.insertBefore(document.createTextNode("<"), target);
  target.parentNode.insertBefore(link, target);
  target.parentNode.insertBefore(document.createTextNode(">"), target);
  target.parentNode.removeChild(target);
  return false;
}

document.addEventListener('DOMContentLoaded', function () {
	let links = document.querySelectorAll('.showaddr');
	links.forEach(function (link) {
		link.addEventListener('click', function (event) {
			event.preventDefault();
			const result = showaddr(event.target)
		});
	});
});

function checkFritzBox(contextPath, button) {
	if (!button.classList.contains("is-info")) {
		return true;
	}
	
	button.classList.remove("state-initial");
	button.classList.add("is-loading");
	button.classList.add("state-searching");
	
	var image = new Image();
	
	image.onload = function() {
		button.classList.remove("is-loading");
		button.classList.remove("state-searching");
		button.classList.remove("is-info");
		button.classList.add("is-primary");
		button.classList.add("state-success");
		document.getElementById("search-fritzbox").href= contextPath + "/anrufbeantworter";
		document.getElementById("fritzbox").src = contextPath + "/assets/img/fritzbox-found.png";
	}
	image.onerror = function() {
		button.classList.remove("is-loading");
		button.classList.remove("state-searching");
		button.classList.remove("is-info");
		button.classList.add("is-danger");
		button.classList.add("state-failed");
		document.getElementById("search-fritzbox").href= contextPath + "/no-fritzbox";
		document.getElementById("fritzbox").src = contextPath + "/assets/img/fritzbox-not-found.png";
	}
	
	image.src = "http://fritz.box/favicon.ico";
	return false;
}

function getContextBasePath() {
	return  document.getElementById("context-path").value;
}

document.addEventListener('DOMContentLoaded', function () {
	let button = document.getElementById('search-fritzbox');
	if (button) {
		button.addEventListener('click', function (event) {
			if (button.classList.contains("state-initial")) {
				event.preventDefault();
				const result = checkFritzBox(getContextBasePath(), button);
			}
		});
	}
});

function copyToClipboard(id) {
	var element = document.getElementById(id);
	window.navigator.clipboard.writeText(element.textContent); 
	return false;
}

document.addEventListener('DOMContentLoaded', function () {
	let links = document.querySelectorAll('.copyToClipboard');
	links.forEach(function (link) {
		link.addEventListener('click', function (event) {
			event.preventDefault();
			const idSource = link.id;
			const id = idSource.substring(0, idSource.indexOf("_"))
			const result = copyToClipboard(id)
		});
	});
});

document.addEventListener('DOMContentLoaded', function () {
	const voteLinks = document.querySelectorAll('.commentVote');

	voteLinks.forEach(function (link) {
		link.addEventListener('click', function (event) {
			const votePath = document.getElementById("votePath");
			if(!votePath?.value) {
				throw new Error("no vote path");
			}
			const commentId = link.getAttribute('data-comment-id');
			const upId = link.getAttribute('data-vote-up-id');
			const downId = link.getAttribute('data-vote-down-id');
			if (link.classList.contains("thumbs-up")) {
				commentVote(votePath.value, commentId, 1, upId, downId);
			} else if (link.classList.contains("thumbs-down")) {
				commentVote(votePath.value, commentId, -1, upId, downId);
			}
			event.preventDefault();
		});
	});
});

function commentVote(path, commentId, vote, upId, downId) {
	var up = document.getElementById(upId);	
	var down = document.getElementById(downId);
	
	var old = up.getAttribute("data-vote");
	var oldVote = 0;
	if (old) {
		var oldVote = parseInt(old);
		
		if (oldVote == vote) {
			// Vote only once.
			return false;
		}
		
		doVote(up, down, oldVote, -1);	
	}

	doVote(up, down, vote, 1);
	
	up.setAttribute("data-vote", "" + vote);
	
	var url = window.location.protocol + "//" + window.location.host + ":" + window.location.port + path;
	fetch(path + "?id=" + commentId + "&vote=" + vote + "&oldVote=" + oldVote, {
		method: "POST",
		credentials: "same-origin"
	});
	
	return false;
}

function doVote(up, down, direction, inc) {
	var element;
	if (direction > 0) {
		element = up;
	} else {
		element = down;
	}

	var value = parseInt(element.textContent) + inc;
	element.textContent = "" + value;
}

document.addEventListener('DOMContentLoaded', function () {
	let links = document.querySelectorAll('.prevent-default');
	links.forEach(function (link) {
		link.addEventListener('click', function (event) {
			event.preventDefault();
		});
	});
});

// API Key rename functionality
document.addEventListener('DOMContentLoaded', function () {
	// Edit button click handler
	const editButtons = document.querySelectorAll('.apikey-edit-btn');
	editButtons.forEach(function (button) {
		button.addEventListener('click', function (event) {
			event.preventDefault();
			const form = button.closest('.apikey-rename-form');
			const td = form.parentElement;
			const label = td.querySelector('.apikey-label');
			const input = form.querySelector('.apikey-input');
			const saveBtn = form.querySelector('.apikey-save-btn');
			const cancelBtn = form.querySelector('.apikey-cancel-btn');

			// Switch to edit mode
			label.style.display = 'none';
			button.style.display = 'none';
			input.style.display = 'inline-block';
			saveBtn.style.display = 'inline-block';
			cancelBtn.style.display = 'inline-block';
			input.focus();
			input.select();
		});
	});

	// Save button click handler
	const saveButtons = document.querySelectorAll('.apikey-save-btn');
	saveButtons.forEach(function (button) {
		button.addEventListener('click', function (event) {
			event.preventDefault();
			const form = button.closest('.apikey-rename-form');
			form.submit();
		});
	});

	// Cancel button click handler
	const cancelButtons = document.querySelectorAll('.apikey-cancel-btn');
	cancelButtons.forEach(function (button) {
		button.addEventListener('click', function (event) {
			event.preventDefault();
			const form = button.closest('.apikey-rename-form');
			const td = form.parentElement;
			const label = td.querySelector('.apikey-label');
			const input = form.querySelector('.apikey-input');
			const saveBtn = form.querySelector('.apikey-save-btn');
			const editBtn = form.querySelector('.apikey-edit-btn');

			// Cancel editing - restore original state
			label.style.display = 'inline';
			editBtn.style.display = 'inline-block';
			input.style.display = 'none';
			saveBtn.style.display = 'none';
			button.style.display = 'none';
			input.value = input.defaultValue;
		});
	});

	// Handle keyboard shortcuts in input field
	const labelInputs = document.querySelectorAll('.apikey-input');
	labelInputs.forEach(function (input) {
		input.addEventListener('keydown', function (event) {
			const form = input.closest('.apikey-rename-form');
			const cancelBtn = form.querySelector('.apikey-cancel-btn');

			if (event.key === 'Escape') {
				event.preventDefault();
				cancelBtn.click();
			} else if (event.key === 'Enter') {
				event.preventDefault();
				form.submit();
			}
		});
	});
});

// Welcome Message Display Name rename functionality
document.addEventListener('DOMContentLoaded', function () {
	// Edit button click handler
	const editButtons = document.querySelectorAll('.welcomename-edit-btn');
	editButtons.forEach(function (button) {
		button.addEventListener('click', function (event) {
			event.preventDefault();
			const form = button.closest('.welcomename-rename-form');
			const nameSpan = button.parentElement;
			const input = form.querySelector('.welcomename-input');
			const saveBtn = form.querySelector('.welcomename-save-btn');
			const cancelBtn = form.querySelector('.welcomename-cancel-btn');

			// Switch to edit mode - hide name/edit button, show input/save/cancel
			nameSpan.style.display = 'none';
			input.style.display = 'inline-block';
			saveBtn.style.display = 'inline-block';
			cancelBtn.style.display = 'inline-block';
			input.focus();
			input.select();
		});
	});

	// Save button click handler
	const saveButtons = document.querySelectorAll('.welcomename-save-btn');
	saveButtons.forEach(function (button) {
		button.addEventListener('click', function (event) {
			event.preventDefault();
			const form = button.closest('.welcomename-rename-form');
			form.submit();
		});
	});

	// Cancel button click handler
	const cancelButtons = document.querySelectorAll('.welcomename-cancel-btn');
	cancelButtons.forEach(function (button) {
		button.addEventListener('click', function (event) {
			event.preventDefault();
			const form = button.closest('.welcomename-rename-form');
			const nameSpan = form.querySelector('.welcomename-edit-btn').parentElement;
			const input = form.querySelector('.welcomename-input');
			const saveBtn = form.querySelector('.welcomename-save-btn');

			// Cancel editing - restore original state, show name/edit button, hide input/save/cancel
			nameSpan.style.display = 'inline';
			input.style.display = 'none';
			saveBtn.style.display = 'none';
			button.style.display = 'none';
			input.value = input.defaultValue;
		});
	});

	// Handle keyboard shortcuts in input field
	const welcomenameInputs = document.querySelectorAll('.welcomename-input');
	welcomenameInputs.forEach(function (input) {
		input.addEventListener('keydown', function (event) {
			const form = input.closest('.welcomename-rename-form');
			const cancelBtn = form.querySelector('.welcomename-cancel-btn');

			if (event.key === 'Escape') {
				event.preventDefault();
				cancelBtn.click();
			} else if (event.key === 'Enter') {
				event.preventDefault();
				form.submit();
			}
		});
	});
});