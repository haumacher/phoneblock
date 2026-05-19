/*
 * Toggle between view-mode and edit-mode on the personal blacklist/whitelist pages.
 *
 * Each row is wrapped in a `.list-entry` element that contains exactly one
 * `.entry-view` (number, rating tag, edit pencil, comment) and one
 * `.entry-edit` (form with textarea and save/cancel buttons). The pencil
 * button hides the view block and shows the form; the cancel button does
 * the opposite.
 */
(function () {
	'use strict';

	function findEntry(el) {
		return el.closest('.list-entry');
	}

	function showEdit(entry) {
		var view = entry.querySelector('.entry-view');
		var edit = entry.querySelector('.entry-edit');
		if (view) {
			view.style.display = 'none';
		}
		if (edit) {
			edit.style.display = '';
			var textarea = edit.querySelector('textarea');
			if (textarea) {
				textarea.focus();
			}
		}
	}

	function showView(entry) {
		var view = entry.querySelector('.entry-view');
		var edit = entry.querySelector('.entry-edit');
		if (edit) {
			edit.style.display = 'none';
		}
		if (view) {
			view.style.display = '';
		}
	}

	function init() {
		document.querySelectorAll('.list-entry .edit-toggle').forEach(function (btn) {
			btn.addEventListener('click', function () {
				showEdit(findEntry(btn));
			});
		});
		document.querySelectorAll('.list-entry .edit-cancel').forEach(function (btn) {
			btn.addEventListener('click', function () {
				showView(findEntry(btn));
			});
		});
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', init);
	} else {
		init();
	}
})();
