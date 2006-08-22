#!/usr/bin/perl

=head1 NAME 

prep.pl - Script that prepares the developer's guide for publication.

=head1 SYNOPSIS

Prepare the Volity Developer's Guide, Perl Edition:

 $ ./prep.pl perl

=head1 DESCRIPTION

This script creates a file called language-entities.dtd, which is
necessary to dereference the entity references that appear in
devguide_core.xml. You feed it one argument on the command line, the
name of a programming-language subdirectory in the devguide directory,
for example "perl". It will use this as the basis for the entity
definitions it creates.

So, basically, before you publish the devguide to some target format,
you must choose a programming language it will cover, and run this
script as appropriate.

It needs to be sitting in the devguide directory to work right.

=head1 SEE ALSO

The README.txt file that should be right next to this script.

=head1 AUTHOR

Jason McIntosh <jmac@jmac.org>

=cut

use warnings;
use strict;
use File::Spec;

my ($vol, $cvs_dir) = File::Spec->splitpath($0);
chdir ($cvs_dir) or die "Can't chdir to $cvs_dir: $!";
my $readme = File::Spec->catfile($cvs_dir, "README.txt");

my ($dir) = @ARGV;

unless ($dir) {
    die "Usage: $0 [language-directory]\nRead $readme for more information.\n";
}

unless (-e $dir) {
    die "There doesn't seem to be a language-entity directory named '$dir'.\nRead $readme for more information.\n";
}

foreach (qw(subtitle author language software)) {
    my $file = File::Spec->catfile($dir, "$_.xml");
    unless (-r $file) {
	die "Can't read required file $file: $!\n Read $readme for more information.\n";
    }
    unless (-f $file) {
	die "Required file $file exists but, er, doesn't actually look like a file.\nRead $readme for more information.\n";
    }
}

my $output_filename = File::Spec->catfile($cvs_dir, "language-entities.dtd");
open (OUT, ">$output_filename") or die "Can't write to $output_filename: $!";

print OUT qq{
<!ENTITY language-author SYSTEM "$dir/author.xml">
<!ENTITY language-name SYSTEM "$dir/language.xml">
<!ENTITY language-software SYSTEM "$dir/software.xml">
<!ENTITY language-programming SYSTEM "$dir/programming.xml">
<!ENTITY language-parlor SYSTEM "$dir/parlor.xml">
<!ENTITY language-subtitle SYSTEM "$dir/subtitle.xml">
};
