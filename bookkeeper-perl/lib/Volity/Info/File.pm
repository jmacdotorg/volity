package Volity::Info::File;

use warnings;
use strict;

use Volity::Info::FileFeature;

use base qw(Volity::Info);

__PACKAGE__->table('ui_file');
__PACKAGE__->columns(All=>qw(id name description url player_id ruleset_id reputation));
__PACKAGE__->has_a(player_id=>"Volity::Info::Player");
__PACKAGE__->has_a(ruleset_id=>"Volity::Info::Ruleset");
__PACKAGE__->has_many(features=>["Volity::Info::FileFeature" => 'ui_feature_id'], 'ui_file_id');
__PACKAGE__->has_many(file_languages=>"Volity::Info::FileLanguage", 'ui_file_id');

sub language_codes {
    my $self = shift;
    return map($_->language_code, $self->file_languages);
}

1;
