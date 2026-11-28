#!/usr/bin/env python3
"""Drop whole-line // comments from the web UI before it is gzipped (#676).

    scripts/strip-js-comments.py <in> <out> [--drop <block>]...

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

--drop <block> also removes the lines from `// <block>` to `// </block>`
(the marker lines included): a part of the page that a build does not
serve, like the display's loader (#773) on the C3, which has no display.
Without it the markers are ordinary comments and go with the others.

Needs only python3, which ESP-IDF already requires.
"""

import re
import sys

COMMENT = re.compile(rb"^[ \t]*//")
SCRIPT_TAG = re.compile(rb"<(/?)script\b", re.IGNORECASE)


def strip(lines, html, drop=()):
    in_script = not html
    dropping = None
    for line in lines:
        if dropping:
            if line.strip() == b"// </" + dropping + b">":
                dropping = None
            continue
        marker = line.strip()
        name = marker[4:-1]
        if (marker.startswith(b"// <") and marker.endswith(b">")
                and name in drop):
            dropping = name
            continue
        if in_script and COMMENT.match(line):
            continue
        yield line
        if html:
            # A line that opens and closes a script keeps in_script false.
            for m in SCRIPT_TAG.finditer(line):
                in_script = m.group(1) == b""
    if dropping:
        sys.exit("strip-js-comments: unterminated dropped block")
    if html and in_script:
        sys.exit("strip-js-comments: unterminated <script> block")


def main():
    args = sys.argv[1:]
    drop = []
    while len(args) >= 2 and args[-2] == "--drop":
        drop.insert(0, args[-1].encode())
        args = args[:-2]
    if len(args) != 2:
        sys.exit("usage: strip-js-comments.py <in> <out> "
                 "[--drop <block>]...")
    src, dst = args
    with open(src, "rb") as f:
        lines = f.readlines()
    out = b"".join(strip(lines, src.endswith(".html"), tuple(drop)))
    with open(dst, "wb") as f:
        f.write(out)


if __name__ == "__main__":
    main()
