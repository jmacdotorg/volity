package Volity::Info::Factory;

use warnings; use strict;

use base qw(Volity::Info);

Volity::Info::Factory->table('factory');
Volity::Info::Factory->columns(All=>qw(id jid ruleset_id));
Volity::Info::Factory->has_a("ruleset_id" => "Volity::Info::Ruleset");

Volity::Info::Ruleset->has_many(factories => "Volity::Info::Factory", "ruleset_id");

1;
