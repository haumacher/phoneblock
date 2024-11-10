function showFB() {
    if (window.fbWindow != null && !window.fbWindow.closed) {
        window.fbWindow.focus();
    } else {
        window.fbWindow = window.open("http://fritz.box", "fritzbox");
    }
    return false;
}
function showAB() {
    if (window.abWindow != null && !window.abWindow.closed) {
        window.abWindow.focus();
    } else {
        window.abWindow = window.open(window.location.host + "/ab/", "phoneblock-ab");
    }
    return false;
}

document.addEventListener('DOMContentLoaded', function () {
    let links = document.querySelectorAll('.showAB');
    links.forEach(function (link) {
        link.addEventListener('click', function (event) {
            const result = showAB()
            if (!result) {
                event.preventDefault();
            }
        });
    });
});

document.addEventListener('DOMContentLoaded', function () {
    let links = document.querySelectorAll('.showFB');
    links.forEach(function (link) {
        link.addEventListener('click', function (event) {
            const result = showFB()
            if (!result) {
                event.preventDefault();
            }
        });
    });
});