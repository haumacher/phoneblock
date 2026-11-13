document.addEventListener('DOMContentLoaded', function () {
    bulmaCollapsible.attach();
});

// Close buttons of notifications (inline onclick handlers are blocked by the CSP).
document.addEventListener('click', function (event) {
    const button = event.target.closest('.notification > .delete');
    if (button) {
        button.parentElement.style.display = 'none';
    }
});
