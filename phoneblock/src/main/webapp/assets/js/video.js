/*
 * Two-click video embedding: the page only shows a locally hosted preview
 * image. The YouTube player (privacy-enhanced domain youtube-nocookie.com) is
 * loaded only after the visitor clicks, so no data reaches Google before that.
 */
(function() {
	function play(box) {
		var id = box.getAttribute('data-video-id');
		if (!/^[A-Za-z0-9_-]{11}$/.test(id)) return;

		var frame = document.createElement('iframe');
		frame.src = 'https://www.youtube-nocookie.com/embed/' + id + '?autoplay=1&rel=0';
		var preview = box.querySelector('img');
		frame.title = (preview && preview.alt) || 'YouTube';
		frame.allow = 'autoplay; encrypted-media; picture-in-picture; fullscreen';
		frame.allowFullscreen = true;
		frame.referrerPolicy = 'strict-origin-when-cross-origin';

		var stage = box.querySelector('.pb-video-stage');
		stage.replaceChildren(frame);
		frame.focus();
	}

	document.querySelectorAll('.pb-video').forEach(function(box) {
		var button = box.querySelector('.pb-video-play');
		if (button) {
			button.addEventListener('click', function() { play(box); });
		}
	});
})();
