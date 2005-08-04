package Volity::Info::FileFeature;

use warnings;
use strict;

use base qw(Volity::Info);

__PACKAGE__->table('ui_file_feature');
__PACKAGE__->columns(All=>qw(id ui_file_id ui_feature_id));
__PACKAGE__->has_a(ui_file_id=>"Volity::Info::File");
__PACKAGE__->has_a(ui_feature_id=>"Volity::Info::Feature");

1;
