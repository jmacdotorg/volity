use strict;
use warnings;
use ExtUtils::MakeMaker;

WriteMakefile(
    NAME                => 'Volity::WebClient',
    AUTHOR              => 'Jason McIntosh <jmac@jmac.org>',
    VERSION_FROM        => 'lib/Volity/WebClient.pm',
    ABSTRACT_FROM       => 'lib/Volity/WebClient.pm',
    PL_FILES            => {},
    PREREQ_PM => {
                  'Test::More'        => 0,
                  'Volity'            => 0.7,
                  'Object::InsideOut' => 0,
                  'Readonly'          => 0,
                  'POE'               => 0,
                  'YAML'              => 0,
                  'Data::JavaScript::Anon' => 0,
                  'Perl6::Slurp'      => 0,
                  'LWP'               => 0,
                  'DBI'               => 0,
              },
    dist                => { COMPRESS => 'gzip -9f', SUFFIX => 'gz', },
    clean               => { FILES => 'Volity-WebClient-*' },
);

do 't/set_up_test_config.pl';
