package Volity::Info::Resource;

use warnings;
use strict;

use base qw(Volity::Info);

__PACKAGE__->table('resource');
__PACKAGE__->columns(All=>qw(id name description url player_id resource_uri_id reputation));
__PACKAGE__->has_a(player_id=>"Volity::Info::Player");
__PACKAGE__->has_a(resource_uri_id=>"Volity::Info::ResourceURI");
__PACKAGE__->has_many(resource_languages=>"Volity::Info::ResourceLanguage", 'resource_id');

Volity::Info::Resource->set_sql(clear_languages=>qq{delete from resource_language where resource_id = ?});



sub language_codes {
    my $self = shift;
    return map($_->language_code, $self->file_languages);
}

sub clear_languages {
    my $self = shift;
    my $sth = $self->sql_clear_languages;
    $sth->execute($self->id);
}

1;
