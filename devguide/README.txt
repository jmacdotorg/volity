This directory contains DocBook XML files and Perl scripts needed to
build the Volity Developer's Guide (VDG). It is cleverly structured so
that authors can add in language-specific sections without having to
duplicate all the common sections.

CONTENTS OF THIS DIRECTORY

* LICENSE.html
Standard-issue blob of HTML proclaiming that The Volity Developer's
Guide, and all its constituent files, are shared under a Creative
Commons Attribution-ShareAlike 2.5 License.

* devguide_core.xml
A DocBook XML document containing all the (parlor-)language-neutral
parts of the Volity Developer's Guide (VDG). It uses entity references to
pull in the language-specific sections.

* prep.pl
A Perl script that, given the name of a subdirectory containing
language-specific XML for the VDG, creates a file called
language_entities.dtd. This file defines some XML entity references
found in devguide_core.xml to point to files found within your
language-specific directory. Until you run this script, the VDG XML
file contains undefined entity references.

Example: To make the VDG Perl-specific:
./prep.pl perl

* perl
A subdirectory containing Perl-specific sections of the VDG. You can
use its structure as a model for language directories you add
yourself. See the more detailed description of this process below.

MAKING A LANGUAGE-SPECIFIC VERSION

First, add an appropriately named directory at the root level, where
the "perl" directory is.

Then, create the following files inside your new directory, with
content as described below:

* language.xml
The name of the language, in plain text. For example, "Python" or
"C++". (Yes, without the quotes. Yes, that's the whole file.)

* author.xml
Zero or more DocBook XML <author> elements.

* software.xml
One or more DocBook XML <para> elements describing the libraries and
other software necessary to write Volity games in the language you're
describing. Specific instructions for obtaining this software would be
nice. These paragraphs will get inserted into a larger section listing
the things that a game developer will need in order to create Volity
games.

* programming.xml
Writing this file will be the bulk of your work here. It contains one
DocBook XML <section> element describing everything the reader needs
to know in order to create a game module in your language, using the
tools you described in the software.xml file. Consider it an article
in itself, with all the figures, cross-references, code examples and
subsections as your heart desires.

You should include a Rock Paper Scissors example to match the ruleset
that was sketched in the section that will appear before this one, but
you can also provide additional examples if you'd like.

* parlor.xml
A DocBook XML <section> element describing how to run a parlor that
runs the game module written in your language.

TODO
(besides content)
* Add license information to the book file somehow.
* Add instructions for publishing to different formats (html, pdf, ...)
