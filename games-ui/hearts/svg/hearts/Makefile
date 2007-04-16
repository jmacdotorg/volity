# The locale directory has subdirs with two-letter language codes, and under
# them various types of files exist.  All will have three letter extensions,
# so *.* will pull just the content files, and not the .svn (or similar)
# control files.
depends=main.svg hearts.js widgets.js deck/*.svg locale/*/*.*
hearts.zip: $(depends)
	zip hearts.zip $(depends)
