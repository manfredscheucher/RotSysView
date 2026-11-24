"""Compare the Terms & privacy text in Dialogs.kt word for word with the Draft section of
misc/turingmachine/doc/privacy-github-pages.md."""
import re, sys
draft = open(sys.argv[1]).read().split("## Draft", 1)[1].split("\n", 1)[1].split("\nTone:", 1)[0]
draft = " ".join(l.lstrip("> ").strip() for l in draft.splitlines())
draft = draft.replace("(Compose sites only:) ", "").replace("[privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement)", "privacy statement")
draft = re.sub(r"\*\*[^*]+\*\*", " ", draft)
kt = open(sys.argv[2]).read()
start = kt.index("private val WEB_PRIVACY"); end = kt.index("private const val ABOUT")
strings = re.findall(r'"((?:[^"\\]|\\.)*)"', kt[start:end])
# literals inside one paragraph are joined directly, paragraphs (list items, separate consts) with a space
s = kt[start:end]
s = re.sub(r'"\s*\+\s*"', "", s)
s = " ".join(x for x in re.findall(r'"((?:[^"\\]|\\.)*)"', s) if not x.startswith("https://")) + "!"
# the parts in order: privacy paragraphs, hosting before + link + after, terms before + link + "!"
s = s.replace("details are in GitHub's ", "details are in GitHub's privacy statement").replace("general feedback or ideas, ", "general feedback or ideas, please let me know").replace("privacy statement .", "privacy statement.")
norm = lambda t: re.findall(r"[\w'.,;:!()\-]+", t.lower())
a, b = norm(draft), norm(s)
print("MATCH" if a == b else f"DIFF\n{a}\n{b}")
