package Volity::Info::FileLanguage;

use warnings;
use strict;

use base qw(Volity::Info);

__PACKAGE__->table('ui_file_language');
__PACKAGE__->columns(All=>qw(id ui_file_id language_code));
__PACKAGE__->has_a(ui_file_id=>"Volity::Info::File");

1;
