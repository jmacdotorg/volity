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

Volity::Info::File->set_sql(clear_features=>qq{delete from ui_file_feature where ui_file_id = ?});

Volity::Info::File->set_sql(clear_languages=>qq{delete from ui_file_language where ui_file_id = ?});

sub language_codes {
    my $self = shift;
    return map($_->language_code, $self->file_languages);
}

sub clear_features {
    my $self = shift;
    my $sth = $self->sql_clear_features;
    $sth->execute($self->id);
}

sub clear_languages {
    my $self = shift;
    my $sth = $self->sql_clear_languages;
    $sth->execute($self->id);
}

1;
