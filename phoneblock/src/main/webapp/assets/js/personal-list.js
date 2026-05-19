/*
 * Inline comment editor for personal blacklist/whitelist entries.
 *
 * Each row (.list-entry) keeps its checkbox, number, rating and vote tags
 * permanently visible. Clicking the pencil button (.edit-toggle) hides the
 * comment paragraph (.entry-comment, optional) plus the pencil itself, and
 * reveals an inline form (.entry-edit) for editing the comment. Cancel
 * (.edit-cancel) restores the original state.
 */
(function () {
	'use strict';

	function show(el) {
		if (el) {
			el.style.display = '';
		}
	}

	function hide(el) {
		if (el) {
			el.style.display = 'none';
		}
	}

	function findEntry(el) {
		return el.closest('.list-entry');
	}

	function showEdit(entry) {
		hide(entry.querySelector('.entry-comment'));
		hide(entry.querySelector('.edit-toggle'));
		var edit = entry.querySelector('.entry-edit');
		show(edit);
		if (edit) {
			var textarea = edit.querySelector('textarea');
			if (textarea) {
				textarea.focus();
			}
		}
	}

	function showView(entry) {
		hide(entry.querySelector('.entry-edit'));
		show(entry.querySelector('.entry-comment'));
		show(entry.querySelector('.edit-toggle'));
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
