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
  
	document.getElementById("pb-seach-input").addEventListener("keypress", function(event) {
	  if (event.key === "Enter") {
	    event.preventDefault();
	    
	    document.getElementById("pb-seach-button").click();
	  }
	}); 
	
	makeSearchButton("pb-seach-button", "pb-seach-input");
});

function makeSearchButton(buttonId, inputId) {
	document.getElementById(buttonId).addEventListener("click", function(event) {
		searchNumber(inputId);
	});
	
	document.getElementById(inputId).addEventListener("keypress", function(event) {
	  if (event.key === "Enter") {
	    event.preventDefault();
		searchNumber(inputId);
	  }
	});
}

function searchNumber(inputId) {
  var number = document.getElementById(inputId).value;
  var location = document.location;
  var slashIndex = location.pathname.indexOf("/", 1);
  var contextPath = location.pathname.substring(0, slashIndex);
  var url = location.protocol + "//" + location.host + contextPath + "/nums/" + number;
  location.assign(url);
}

function showaddr(target) {
  var addr =
    "haui" + String.fromCharCode(64) + "haumacher" +
    String.fromCharCode(46) + "de";
  var link = document.createElement("a");
  link.setAttribute("href", "mailto:" + addr);
  link.appendChild(
    document.createTextNode(addr));
  target.parentNode.replaceChild(link, target);
  return false;
}

function checkFritzBox(contextPath, button) {
	if (!button.classList.contains("is-info")) {
		return true;
	}
	
	button.classList.add("is-loading");
	button.textContent = "Moment..."

	var image = new Image();
	
	image.onload = function() {
		button.classList.remove("is-loading");
		button.classList.remove("is-info");
		button.classList.add("is-primary");
		button.textContent = "Gefunden, leg los!"
		document.getElementById("search-fritzbox").href="setup.jsp";
		document.getElementById("fritzbox").src = contextPath + "/fritzbox-found.png";
	}
	image.onerror = function() {
		button.classList.remove("is-loading");
		button.classList.remove("is-info");
		button.classList.add("is-danger");
		button.textContent = "Nicht gefunden, was tun?";
		document.getElementById("search-fritzbox").href="no-fritzbox.jsp";
		document.getElementById("fritzbox").src = contextPath + "/fritzbox-not-found.png";
	}
	
	image.src = "http://fritz.box/favicon.ico";
	return false;
}
