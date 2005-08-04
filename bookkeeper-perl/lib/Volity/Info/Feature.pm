package Volity::Info::Feature;

use warnings;
use strict;

use Volity::Info::FileFeature;

use base qw(Volity::Info);

__PACKAGE__->table('ui_feature');
__PACKAGE__->columns(All=>qw(id name description uri));
__PACKAGE__->has_many(files=>["Volity::Info::FileFeature" => 'ui_file_id'], 'ui_feature_id');

1;
