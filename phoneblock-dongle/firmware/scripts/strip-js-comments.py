#!/usr/bin/env python3
"""Drop whole-line // comments from the web UI before it is gzipped (#676).

    scripts/strip-js-comments.py <in> <out>

The SPA (main/web/index.html) and rec.js are gzipped but not minified, so
their comments end up in the app image: about 18 KB of the gzipped
index.html, a large share of the C3's free app slot. The comments stay in
the source; only the embedded copy loses them.

Removed are lines whose first non-blank characters are //, in a .html file
only inside <script> blocks, in a .js file everywhere. Everything else is
copied byte for byte, so line-end and inline comments stay. A // line
inside a template literal would be text, not a comment, and would be
dropped too: test/strip_web_comments_js_check.js checks that every dropped
line really is a comment.

Needs only python3, which ESP-IDF already requires.
"""

import re
import sys

COMMENT = re.compile(rb"^[ \t]*//")
SCRIPT_TAG = re.compile(rb"<(/?)script\b", re.IGNORECASE)


def strip(lines, html):
    in_script = not html
    for line in lines:
        if in_script and COMMENT.match(line):
            continue
        yield line
        if html:
            # A line that opens and closes a script keeps in_script false.
            for m in SCRIPT_TAG.finditer(line):
                in_script = m.group(1) == b""
    if html and in_script:
        sys.exit("strip-js-comments: unterminated <script> block")


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: strip-js-comments.py <in> <out>")
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, "rb") as f:
        lines = f.readlines()
    out = b"".join(strip(lines, src.endswith(".html")))
    with open(dst, "wb") as f:
        f.write(out)


if __name__ == "__main__":
    main()
